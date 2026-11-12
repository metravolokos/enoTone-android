package app.enotone.android

import app.enotone.library.ManagedIndexProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRefreshPolicyTest {
    private fun running(saved: Int) = ManagedScanStatus(ManagedScanState.RUNNING,
        progress = ManagedIndexProgress(completedBatches = 0, attempted = saved, savedWithTags = saved,
            completedWithoutTags = 0, retryableErrors = 0, permanentErrors = 0, staleWrites = 0,
            getRequests = 0, downloadedBytes = 0, remaining = 0, stopCode = "NONE"))

    @Test fun `indexing end and large progress steps refresh the library`() {
        assertTrue(LibraryRefreshPolicy.indexingChanged(running(10), ManagedScanStatus(ManagedScanState.COMPLETE)))
        assertTrue(LibraryRefreshPolicy.indexingChanged(running(10), ManagedScanStatus(ManagedScanState.PAUSED)))
        assertTrue(LibraryRefreshPolicy.indexingChanged(running(499), running(500)))
        assertFalse(LibraryRefreshPolicy.indexingChanged(running(10), running(400)))
        assertFalse(LibraryRefreshPolicy.indexingChanged(ManagedScanStatus(), ManagedScanStatus()))
    }

    @Test fun `artwork preparation end refreshes covers`() {
        val runningArt = ArtworkScanStatus(ArtworkScanState.RUNNING, completed = 1, total = 2)
        assertTrue(LibraryRefreshPolicy.artworkChanged(runningArt,
            ArtworkScanStatus(ArtworkScanState.COMPLETE, completed = 2, total = 2)))
        assertFalse(LibraryRefreshPolicy.artworkChanged(ArtworkScanStatus(), ArtworkScanStatus()))
    }

    @Test fun `watcher turns service transitions into revisions`() = runTest {
        val indexing = MutableStateFlow(ManagedScanStatus())
        val artwork = MutableStateFlow(ArtworkScanStatus())
        var changes = 0
        val job = LibraryDataRevision.watch(this, indexing, artwork) { changes++ }
        runCurrent()
        indexing.value = running(1)
        runCurrent()
        indexing.value = ManagedScanStatus(ManagedScanState.COMPLETE)
        runCurrent()
        artwork.value = ArtworkScanStatus(ArtworkScanState.RUNNING, completed = 0, total = 1)
        runCurrent()
        artwork.value = ArtworkScanStatus(ArtworkScanState.COMPLETE, completed = 1, total = 1)
        runCurrent()
        assertEquals(2, changes)
        job.cancel()
    }
}
