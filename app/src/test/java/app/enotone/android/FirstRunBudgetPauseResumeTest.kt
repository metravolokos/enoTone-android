package app.enotone.android

import app.enotone.library.ManagedIndexProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A bounded-budget pause must resume by itself; a user Pause must not.
 *
 * `FirstRunImportEffect` clears its one-shot `started` flag only when this predicate holds, and
 * the first-run indexing screen has no resume action of its own — so a wrong answer either
 * freezes the screen on partial progress (#257) or silently overrides the user's Pause.
 */
class FirstRunBudgetPauseResumeTest {
    private fun paused(code: String, attempted: Int, terminating: Boolean = false) =
        ManagedScanStatus(
            ManagedScanState.PAUSED,
            progress = ManagedIndexProgress(
                completedBatches = 1, attempted = attempted, savedWithTags = attempted,
                completedWithoutTags = 0, retryableErrors = 0, permanentErrors = 0, staleWrites = 0,
                getRequests = attempted, downloadedBytes = 0, remaining = 0, stopCode = code,
            ),
            terminating = terminating,
        )

    @Test
    fun `a budget pause with progress resumes`() {
        assertTrue(budgetPauseNeedsResume(paused("SESSION_BATCH_LIMIT", attempted = 4000)))
        assertTrue(budgetPauseNeedsResume(paused("SESSION_TIME_BUDGET", attempted = 12)))
    }

    @Test
    fun `a user pause stays sticky`() {
        assertFalse(budgetPauseNeedsResume(paused("PAUSED", attempted = 4000)))
    }

    @Test
    fun `a budget pause without progress must not loop`() {
        assertFalse(budgetPauseNeedsResume(paused("SESSION_BATCH_LIMIT", attempted = 0)))
    }

    @Test
    fun `a terminating pause waits for teardown`() {
        assertFalse(
            budgetPauseNeedsResume(paused("SESSION_BATCH_LIMIT", attempted = 10, terminating = true)),
        )
    }

    @Test
    fun `only PAUSED can resume`() {
        assertFalse(budgetPauseNeedsResume(ManagedScanStatus(ManagedScanState.RUNNING)))
        assertFalse(budgetPauseNeedsResume(ManagedScanStatus(ManagedScanState.COMPLETE)))
        assertFalse(budgetPauseNeedsResume(ManagedScanStatus(ManagedScanState.ERROR)))
        assertFalse(budgetPauseNeedsResume(ManagedScanStatus()))
    }

    @Test
    fun `a pause without a progress snapshot does not resume`() {
        assertFalse(budgetPauseNeedsResume(ManagedScanStatus(ManagedScanState.PAUSED)))
    }
}
