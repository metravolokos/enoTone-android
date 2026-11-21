package app.enotone.android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournaledAccountSignOutTest {
    @Test fun `failed durable journal write prevents destructive cleanup`() = runTest {
        var cleanupCalls = 0
        var failed = false
        try {
            JournaledAccountSignOut(
                writePending = { false },
                cleanup = { cleanupCalls++ },
            ).run("a".repeat(64))
        } catch (_: IllegalStateException) {
            failed = true
        }

        assertTrue(failed)
        assertEquals(0, cleanupCalls)
    }
}
