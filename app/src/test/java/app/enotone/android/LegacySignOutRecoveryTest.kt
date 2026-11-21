package app.enotone.android

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Decision-table tests for the legacy sign-out recovery introduced by ADR-0103
 * (Issue #240). The decision is pure, so no mocks or process-death simulation is
 * needed: every window of the sign-out sequence maps to one row here.
 */
class LegacySignOutRecoveryTest {
    private fun state(
        pending: Boolean = true,
        hasStoredLogin: Boolean = false,
        libraryRowCount: Int = 0,
        hasAnyManifest: Boolean = true,
    ) = LegacySignOutState(pending, hasStoredLogin, libraryRowCount, hasAnyManifest)

    @Test
    fun `no pending sign-out needs no recovery`() {
        assertEquals(LegacySignOutAction.NONE, LegacySignOutRecovery.decide(state(pending = false)))
    }

    @Test
    fun `pending sign-out with a stored login belongs to the newer journal path`() {
        // PR #239 owns this state: the user retries "Выйти" with the saved login.
        assertEquals(
            LegacySignOutAction.NONE,
            LegacySignOutRecovery.decide(state(hasStoredLogin = true)),
        )
    }

    @Test
    fun `legacy journal with an empty vault completes the credential tail`() {
        // Credentials are deleted last, so an absent login proves the cleanup finished.
        assertEquals(
            LegacySignOutAction.COMPLETE_TAIL,
            LegacySignOutRecovery.decide(state(hasStoredLogin = false, hasAnyManifest = true)),
        )
    }

    @Test
    fun `manifest surviving without library rows completes the tail`() {
        // Process died inside the tail: Room rows are gone, the manifest remains.
        // Distinguished from the case above by the explicit zero row count while the
        // ledger still names the account's reference hashes.
        assertEquals(
            LegacySignOutAction.COMPLETE_TAIL,
            LegacySignOutRecovery.decide(
                state(libraryRowCount = 0, hasAnyManifest = true),
            ),
        )
    }

    @Test
    fun `both artefacts absent still completes the tail`() {
        // Nothing is left to identify: the marker alone is the only residue.
        assertEquals(
            LegacySignOutAction.COMPLETE_TAIL,
            LegacySignOutRecovery.decide(state(libraryRowCount = 0, hasAnyManifest = false)),
        )
    }

    @Test
    fun `library rows without a manifest stay fail closed`() {
        // A finished cleanup cannot leave tracks behind, so the marker may be stale
        // for a reason outside the ordering invariant: the owner must decide.
        assertEquals(
            LegacySignOutAction.BLOCKED,
            LegacySignOutRecovery.decide(state(libraryRowCount = 12, hasAnyManifest = false)),
        )
    }

    @Test
    fun `library rows with a manifest remain recoverable`() {
        // The manifest is written before Room deletion, so its presence is consistent.
        assertEquals(
            LegacySignOutAction.COMPLETE_TAIL,
            LegacySignOutRecovery.decide(state(libraryRowCount = 12, hasAnyManifest = true)),
        )
    }

    @Test
    fun `a stored login outranks every other signal`() {
        for (rowCount in listOf(0, 5)) {
            for (manifest in listOf(true, false)) {
                assertEquals(
                    "stored login with rows=$rowCount manifest=$manifest",
                    LegacySignOutAction.NONE,
                    LegacySignOutRecovery.decide(
                        state(hasStoredLogin = true, libraryRowCount = rowCount, hasAnyManifest = manifest),
                    ),
                )
            }
        }
    }
}
