package app.enotone.android

import org.json.JSONArray
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Формат зашифрованного входа: версия, длина IV, IV, AES-GCM шифротекст с тегом.
 * IV при каждом шифровании генерирует сам провайдер (Android Keystore не принимает чужой IV),
 * поэтому повторное использование IV исключено построением. Без Context и Keystore —
 * проверяется JUnit с программным ключом (сценарий ACC-06).
 *
 * v1: [email, password] — источник по умолчанию (Mail.ru /AllMusic).
 * v2: [email, password, origin, rootFolder] — выбранная папка/провайдер (ADR-0106).
 * Сохранённый v1 читается как источник по умолчанию без миграции данных.
 */
internal object CredentialEnvelope {
    private const val VERSION_V1: Byte = 1
    private const val VERSION_V2: Byte = 2
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / Byte.SIZE_BITS
    private const val HEADER = 2

    /** Число полей v1 (email, password) и v2 (+ origin, rootFolder) в сохранённом JSON. */
    private const val V1_FIELD_COUNT = 2
    private const val V2_FIELD_COUNT = 4
    private const val ORIGIN_INDEX = 2
    private const val ROOT_FOLDER_INDEX = 3
    const val MAX_BLOB_BYTES = 16384
    private const val AAD_V1 = "enoTone-WebDAV-v1"
    private const val AAD_V2 = "enoTone-WebDAV-v2"

    fun seal(key: SecretKey, email: String, password: String, endpoint: SourceEndpoint): ByteArray {
        require(email.isNotBlank() && password.isNotBlank()) { "Missing credentials" }
        val plaintext = JSONArray()
            .put(email).put(password)
            .put(endpoint.origin).put(endpoint.rootFolder)
            .toString().toByteArray(Charsets.UTF_8)
        return encrypt(key, VERSION_V2, AAD_V2, plaintext)
    }

    private fun encrypt(key: SecretKey, version: Byte, aad: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        require(iv.size == IV_LENGTH) { "Unexpected cipher IV" }
        val encrypted = cipher.doFinal(plaintext)
        val blob = ByteArray(HEADER + iv.size + encrypted.size)
        blob[0] = version
        blob[1] = iv.size.toByte()
        iv.copyInto(blob, destinationOffset = HEADER)
        encrypted.copyInto(blob, destinationOffset = HEADER + iv.size)
        require(blob.size <= MAX_BLOB_BYTES) { "Credential payload too large" }
        return blob
    }

    /** Бросает исключение на любом повреждении, усечении, чужом ключе или чужой версии. */
    fun open(key: SecretKey, blob: ByteArray): StoredWebDavLogin {
        require(blob.size in (HEADER + IV_LENGTH + TAG_BYTES)..MAX_BLOB_BYTES &&
            (blob[0] == VERSION_V1 || blob[0] == VERSION_V2) && blob[1].toInt() == IV_LENGTH) {
            "Invalid credential envelope"
        }
        val version = blob[0]
        val aad = if (version == VERSION_V2) AAD_V2 else AAD_V1
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key,
            GCMParameterSpec(TAG_BITS, blob.copyOfRange(HEADER, HEADER + IV_LENGTH)))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val json = JSONArray(String(cipher.doFinal(blob.copyOfRange(HEADER + IV_LENGTH, blob.size)),
            Charsets.UTF_8))
        return if (version == VERSION_V1) {
            require(json.length() == V1_FIELD_COUNT) { "Invalid credential contents" }
            val email = json.getString(0)
            val password = json.getString(1)
            require(email.isNotBlank() && password.isNotBlank()) { "Invalid credential contents" }
            StoredWebDavLogin(email, password)
        } else {
            require(json.length() == V2_FIELD_COUNT) { "Invalid credential contents" }
            val email = json.getString(0)
            val password = json.getString(1)
            require(email.isNotBlank() && password.isNotBlank()) { "Invalid credential contents" }
            val endpoint = SourceEndpoint(json.getString(ORIGIN_INDEX), json.getString(ROOT_FOLDER_INDEX))
            StoredWebDavLogin(email, password, endpoint)
        }
    }
}
