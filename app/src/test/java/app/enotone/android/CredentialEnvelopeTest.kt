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
        val first = CredentialEnvelope.seal(key, "user@example.test", "secret")
        val second = CredentialEnvelope.seal(key, "user@example.test", "secret")
        assertEquals(StoredWebDavLogin("user@example.test", "secret"), CredentialEnvelope.open(key, first))
        assertFalse(first.copyOfRange(2, 14).contentEquals(second.copyOfRange(2, 14)))
    }

    @Test fun tamperedTruncatedWrongKeyOrForeignVersionAlwaysThrow() {
        val blob = CredentialEnvelope.seal(key, "user@example.test", "secret")
        val tampered = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        val truncated = blob.copyOf(blob.size - 5)
        val foreign = blob.copyOf().also { it[0] = 2 }
        for (bad in listOf(tampered, truncated, foreign, ByteArray(0))) {
            assertThrows(Exception::class.java) { CredentialEnvelope.open(key, bad) }
        }
        assertThrows(Exception::class.java) { CredentialEnvelope.open(key(), blob) }
    }
}
