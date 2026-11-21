package app.enotone.android

/**
 * Observable facts that decide whether a legacy sign-out can be completed.
 *
 * A legacy journal — one written before PR #239 — carries the pending flag but no
 * cleanup proof and no source key. Rather than guessing, the recovery relies on the
 * ordering invariant of [AccountSignOut]: credentials are deleted last. An absent
 * credential record therefore proves that tracks, artwork and checkpoints were
 * already cleared, so nothing outside the credential tail needs deleting.
 */
internal data class LegacySignOutState(
    val pending: Boolean,
    val hasStoredLogin: Boolean,
    val libraryRowCount: Int,
    val hasAnyManifest: Boolean,
)

/** What the caller must do after inspecting a [LegacySignOutState]. */
internal enum class LegacySignOutAction {
    /** No pending sign-out: nothing to do. */
    NONE,

    /** The tail plus the marker must be cleared; nothing in the library is touched. */
    COMPLETE_TAIL,

    /** Another signal contradicts a finished cleanup: stay fail-closed for the owner. */
    BLOCKED,
}

/** Pure decision table for ADR-0103; performs no I/O so every case is unit-testable. */
internal object LegacySignOutRecovery {
    fun decide(state: LegacySignOutState): LegacySignOutAction = when {
        // A stored login means the ordinary retry path from PR #239 owns this state.
        !state.pending || state.hasStoredLogin -> LegacySignOutAction.NONE
        // Library rows without any manifest contradict a finished cleanup: the marker
        // could have survived outside the ordering invariant, so demand an owner call.
        state.libraryRowCount > 0 && !state.hasAnyManifest -> LegacySignOutAction.BLOCKED
        else -> LegacySignOutAction.COMPLETE_TAIL
    }
}
