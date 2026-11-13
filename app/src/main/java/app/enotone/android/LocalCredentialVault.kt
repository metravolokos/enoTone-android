package app.enotone.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

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
        val encoded = Base64.encodeToString(
            CredentialEnvelope.seal(getOrCreateKey(), email, password), Base64.NO_WRAP)
        check(prefs(context).edit().putString(ENTRY, encoded).commit()) {
            "Unable to persist protected credentials"
        }
    }

    fun restore(context: Context): StoredWebDavLogin? {
        val encoded = prefs(context).getString(ENTRY, null) ?: return null
        // Refuse corrupt/restored blobs: never fall back to plaintext credentials.
        // Any failure throws; AccountSettingsViewModel shows the login form (ACC-06).
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Credential key unavailable")
        return CredentialEnvelope.open(key, Base64.decode(encoded, Base64.NO_WRAP))
    }

    fun clear(context: Context) {
        check(prefs(context).edit().remove(ENTRY).commit()) {
            "Unable to remove protected credentials"
        }
        val store = keyStore()
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
    }
}
