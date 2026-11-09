package app.enotone.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Returned only to the app's in-memory Compose state. Never log or parcel. */
internal data class StoredWebDavLogin(val email: String, val password: String)

/**
 * Explicit opt-in, single-account credential vault. AndroidKeyStore holds a
 * non-exportable AES-256 key; SharedPreferences holds ONLY authenticated
 * AES-GCM ciphertext. No Room, app backup, intents or network operations.
 */
internal object LocalCredentialVault {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "enotone_webdav_credentials_v1"
    private const val PREFERENCES = "enotone_private_auth_v1"
    private const val ENTRY = "encrypted_credentials"
    private const val VERSION: Byte = 1
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    private const val MAX_BLOB_BYTES = 16384
    private val associatedData = "enoTone-WebDAV-v1".toByteArray(Charsets.UTF_8)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun save(context: Context, email: String, password: String) {
        require(email.isNotBlank() && password.isNotBlank()) { "Missing credentials" }
        val plaintext = JSONArray().put(email).put(password)
            .toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
        require(iv.size == IV_LENGTH) { "Unexpected cipher IV" }
        val encrypted = cipher.doFinal(plaintext)
        val blob = ByteArray(2 + iv.size + encrypted.size)
        blob[0] = VERSION
        blob[1] = iv.size.toByte()
        iv.copyInto(blob, destinationOffset = 2)
        encrypted.copyInto(blob, destinationOffset = 2 + iv.size)
        require(blob.size <= MAX_BLOB_BYTES) { "Credential payload too large" }
        val encoded = Base64.encodeToString(blob, Base64.NO_WRAP)
        check(prefs(context).edit().putString(ENTRY, encoded).commit()) {
            "Unable to persist protected credentials"
        }
    }

    fun restore(context: Context): StoredWebDavLogin? {
        val encoded = prefs(context).getString(ENTRY, null) ?: return null
        // Refuse corrupt/restored blobs: never fall back to plaintext credentials.
        val blob = Base64.decode(encoded, Base64.NO_WRAP)
        require(blob.size in (2 + IV_LENGTH + 16)..MAX_BLOB_BYTES &&
            blob[0] == VERSION && blob[1].toInt() == IV_LENGTH) {
            "Invalid credential envelope"
        }
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Credential key unavailable")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key,
            GCMParameterSpec(TAG_BITS, blob.copyOfRange(2, 2 + IV_LENGTH)))
        cipher.updateAAD(associatedData)
        val json = JSONArray(String(cipher.doFinal(blob.copyOfRange(
            2 + IV_LENGTH, blob.size)), Charsets.UTF_8))
        require(json.length() == 2) { "Invalid credential contents" }
        val email = json.getString(0)
        val password = json.getString(1)
        require(email.isNotBlank() && password.isNotBlank()) {
            "Invalid credential contents"
        }
        return StoredWebDavLogin(email, password)
    }

    fun clear(context: Context) {
        check(prefs(context).edit().remove(ENTRY).commit()) {
            "Unable to remove protected credentials"
        }
        val store = keyStore()
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
    }
}
