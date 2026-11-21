package app.enotone.android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

    @Test fun `journal write exception prevents destructive cleanup`() = runTest {
        var cleanupCalls = 0
        val journalFailure = IllegalStateException("journal unavailable")
        var actualFailure: Throwable? = null
        try {
            JournaledAccountSignOut(
                writePending = { throw journalFailure },
                cleanup = { cleanupCalls++ },
            ).run("a".repeat(64))
        } catch (failure: Throwable) {
            actualFailure = failure
        }

        assertSame(journalFailure, actualFailure)
        assertEquals(0, cleanupCalls)
    }
}
