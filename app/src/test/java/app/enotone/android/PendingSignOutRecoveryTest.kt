package app.enotone.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSignOutRecoveryTest {
    private data class State(
        var pending: Boolean = true,
        var cleanupConfirmed: Boolean = false,
        var vaultEntry: Boolean = true,
        var keyAlias: Boolean = true,
        var finishFailures: Int = 0,
        var clearCalls: Int = 0,
    )

    private fun recovery(state: State) = PendingSignOutRecovery(
        pending = { state.pending },
        cleanupConfirmed = { state.cleanupConfirmed },
        clearCredentials = {
            state.clearCalls++
            state.vaultEntry = false
            state.keyAlias = false
        },
        finish = {
            if (state.finishFailures > 0) {
                state.finishFailures--
                error("marker finish interrupted")
            }
            state.pending = false
            state.cleanupConfirmed = false
        },
    )

    @Test fun `every window before durable cleanup proof remains fail closed`() {
        listOf("before Room", "after Room", "after artwork", "after checkpoint").forEach { _ ->
            val state = State(cleanupConfirmed = false)

            assertFalse(recovery(state).run())
            assertTrue(state.pending)
            assertTrue(state.vaultEntry)
            assertTrue(state.keyAlias)
            assertEquals(0, state.clearCalls)
        }
    }

    @Test fun `confirmed cleanup handles vault record and key alias windows idempotently`() {
        listOf(
            State(cleanupConfirmed = true, vaultEntry = true, keyAlias = true),
            State(cleanupConfirmed = true, vaultEntry = false, keyAlias = true),
            State(cleanupConfirmed = true, vaultEntry = false, keyAlias = false),
        ).forEach { state ->
            assertTrue(recovery(state).run())
            assertFalse(state.pending)
            assertFalse(state.vaultEntry)
            assertFalse(state.keyAlias)
            assertEquals(1, state.clearCalls)

            assertTrue(recovery(state).run())
            assertEquals(1, state.clearCalls)
        }
    }

    @Test fun `marker remains recoverable if process dies before finish`() {
        val state = State(cleanupConfirmed = true, finishFailures = 1)

        try {
            recovery(state).run()
            throw AssertionError("Expected interrupted marker finish")
        } catch (_: IllegalStateException) {
            // The credential tail is idempotent; the marker remains until the retry finishes it.
        }

        assertTrue(state.pending)
        assertTrue(state.cleanupConfirmed)
        assertFalse(state.vaultEntry)
        assertFalse(state.keyAlias)
        assertTrue(recovery(state).run())
        assertFalse(state.pending)
        assertEquals(2, state.clearCalls)
    }
}
