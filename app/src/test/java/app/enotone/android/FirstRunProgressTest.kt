package app.enotone.android

import app.enotone.library.ManagedIndexProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunProgressTest {
    @Test fun importedLibraryCountKeepsProgressAcrossNewSessions() {
        val beforeInterruption = calculateFirstRunProgress(
            importedTrackCount = 3590,
            pendingTagCount = 2350,
            progress = progress(attempted = 1240, remaining = 2350),
        )
        val afterRecovery = calculateFirstRunProgress(
            importedTrackCount = 3590,
            pendingTagCount = 2350,
            progress = progress(attempted = 10, remaining = 2340),
        )

        assertEquals(FirstRunProgress(completed = 1240, total = 3590), beforeInterruption)
        assertEquals(FirstRunProgress(completed = 1250, total = 3590), afterRecovery)
        assertTrue(afterRecovery.completed >= beforeInterruption.completed)
    }

    @Test fun usesPendingCountWithoutSessionProgressAndClampsToImportedTotal() {
        assertEquals(
            FirstRunProgress(completed = 1240, total = 3590),
            calculateFirstRunProgress(3590, 2350, null),
        )
        assertEquals(
            FirstRunProgress(completed = 0, total = 3590),
            calculateFirstRunProgress(3590, 5000, null),
        )
        assertEquals(
            FirstRunProgress(completed = 3590, total = 3590),
            calculateFirstRunProgress(3590, -4, null),
        )
    }

    @Test fun keepsTheSessionCalculationWhenImportedCountIsUnknown() {
        assertEquals(
            FirstRunProgress(completed = 10, total = 2350),
            calculateFirstRunProgress(null, 2350, progress(attempted = 10, remaining = 2340)),
        )
        assertEquals(
            FirstRunProgress(completed = 0, total = 2350),
            calculateFirstRunProgress(null, 2350, null),
        )
    }

    private fun progress(attempted: Int, remaining: Int) = ManagedIndexProgress(
        completedBatches = 0,
        attempted = attempted,
        savedWithTags = 0,
        completedWithoutTags = 0,
        retryableErrors = 0,
        permanentErrors = 0,
        staleWrites = 0,
        getRequests = 0,
        downloadedBytes = 0,
        remaining = remaining,
        stopCode = "RUNNING",
    )
}
