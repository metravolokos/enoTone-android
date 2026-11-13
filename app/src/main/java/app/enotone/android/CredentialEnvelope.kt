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
 */
internal object CredentialEnvelope {
    private const val VERSION: Byte = 1
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / Byte.SIZE_BITS
    private const val HEADER = 2
    const val MAX_BLOB_BYTES = 16384
    private val associatedData = "enoTone-WebDAV-v1".toByteArray(Charsets.UTF_8)

    fun seal(key: SecretKey, email: String, password: String): ByteArray {
        require(email.isNotBlank() && password.isNotBlank()) { "Missing credentials" }
        val plaintext = JSONArray().put(email).put(password).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
        require(iv.size == IV_LENGTH) { "Unexpected cipher IV" }
        val encrypted = cipher.doFinal(plaintext)
        val blob = ByteArray(HEADER + iv.size + encrypted.size)
        blob[0] = VERSION
        blob[1] = iv.size.toByte()
        iv.copyInto(blob, destinationOffset = HEADER)
        encrypted.copyInto(blob, destinationOffset = HEADER + iv.size)
        require(blob.size <= MAX_BLOB_BYTES) { "Credential payload too large" }
        return blob
    }

    /** Бросает исключение на любом повреждении, усечении, чужом ключе или чужой версии. */
    fun open(key: SecretKey, blob: ByteArray): StoredWebDavLogin {
        require(blob.size in (HEADER + IV_LENGTH + TAG_BYTES)..MAX_BLOB_BYTES &&
            blob[0] == VERSION && blob[1].toInt() == IV_LENGTH) {
            "Invalid credential envelope"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key,
            GCMParameterSpec(TAG_BITS, blob.copyOfRange(HEADER, HEADER + IV_LENGTH)))
        cipher.updateAAD(associatedData)
        val json = JSONArray(String(cipher.doFinal(blob.copyOfRange(HEADER + IV_LENGTH, blob.size)),
            Charsets.UTF_8))
        require(json.length() == 2) { "Invalid credential contents" }
        val email = json.getString(0)
        val password = json.getString(1)
        require(email.isNotBlank() && password.isNotBlank()) { "Invalid credential contents" }
        return StoredWebDavLogin(email, password)
    }
}
