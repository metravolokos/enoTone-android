package app.enotone.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** ACC-06: повреждённый вход не расшифровывается и не подменяется; Keystore заменён программным AES. */
@RunWith(RobolectricTestRunner::class) // настоящий org.json вместо заглушек android.jar
@Config(sdk = [35])
class CredentialEnvelopeTest {
    private fun key(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val key = key()

    @Test fun roundTripAndFreshIvEachTime() {
        val endpoint = SourceEndpoint.MAILRU_ALL_MUSIC
        val first = CredentialEnvelope.seal(key, "user@example.test", "secret", endpoint)
        val second = CredentialEnvelope.seal(key, "user@example.test", "secret", endpoint)
        assertEquals(StoredWebDavLogin("user@example.test", "secret", endpoint),
            CredentialEnvelope.open(key, first))
        assertFalse(first.copyOfRange(2, 14).contentEquals(second.copyOfRange(2, 14)))
    }

    @Test fun customFolderRoundTripsAndLegacyV1ReadsAsDefaultSource() {
        val endpoint = SourceEndpoint("https://webdav.cloud.mail.ru", "/MP3/Collection")
        val blob = CredentialEnvelope.seal(key, "user@example.test", "secret", endpoint)
        assertEquals(StoredWebDavLogin("user@example.test", "secret", endpoint),
            CredentialEnvelope.open(key, blob))
        // A legacy v1 blob has no endpoint: it must read as the default Mail.ru source.
        // The v1 on-disk format is preserved verbatim here to guard its readability.
        val legacy = sealLegacyV1("user@example.test", "secret")
        assertEquals(StoredWebDavLogin("user@example.test", "secret"),
            CredentialEnvelope.open(key, legacy))
    }

    /** Independent reproduction of the shipped v1 envelope format. */
    private fun sealLegacyV1(email: String, password: String): ByteArray {
        val plain = org.json.JSONArray().put(email).put(password).toString().toByteArray(Charsets.UTF_8)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD("enoTone-WebDAV-v1".toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        val blob = ByteArray(2 + iv.size + encrypted.size)
        blob[0] = 1
        blob[1] = iv.size.toByte()
        iv.copyInto(blob, destinationOffset = 2)
        encrypted.copyInto(blob, destinationOffset = 2 + iv.size)
        return blob
    }

    @Test fun tamperedTruncatedWrongKeyOrForeignVersionAlwaysThrow() {
        val blob = CredentialEnvelope.seal(key, "user@example.test", "secret", SourceEndpoint.MAILRU_ALL_MUSIC)
        val tampered = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        val truncated = blob.copyOf(blob.size - 5)
        val foreign = blob.copyOf().also { it[0] = 3 }
        for (bad in listOf(tampered, truncated, foreign, ByteArray(0))) {
            assertThrows(Exception::class.java) { CredentialEnvelope.open(key, bad) }
        }
        assertThrows(Exception::class.java) { CredentialEnvelope.open(key(), blob) }
    }
}
