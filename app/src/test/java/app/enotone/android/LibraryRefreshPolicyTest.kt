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

    @Test fun `a terminal snapshot refreshes before the service is destroyed`() {
        // The runner publishes the terminal result with terminating = true, so `active` stays
        // true until onDestroy; the revision must follow the transition, not the teardown.
        val terminal = ManagedScanStatus(ManagedScanState.COMPLETE, progress = running(10).progress,
            terminating = true)
        assertTrue("terminal transition must refresh", LibraryRefreshPolicy.indexingChanged(running(10), terminal))
    }

    @Test fun `tracks completed without tags also advance the revision`() {
        fun withoutTags(attempted: Int) = ManagedScanStatus(ManagedScanState.RUNNING,
            progress = ManagedIndexProgress(completedBatches = 0, attempted = attempted, savedWithTags = 0,
                completedWithoutTags = attempted, retryableErrors = 0, permanentErrors = 0, staleWrites = 0,
                getRequests = attempted, downloadedBytes = 0, remaining = 0, stopCode = "NONE"))
        assertTrue(
            "500 tagless tracks must be a step, not a silent no-op",
            LibraryRefreshPolicy.indexingChanged(withoutTags(499), withoutTags(500)),
        )
        assertFalse(
            "a small tagless step stays throttled",
            LibraryRefreshPolicy.indexingChanged(withoutTags(10), withoutTags(400)),
        )
    }

    @Test fun `a finished run bumps exactly once`() {
        // The terminal snapshot arrives with terminating = true and is later cleared by
        // onDestroy. Counting both would reload the catalog twice per run (#258 class).
        val terminal = ManagedScanStatus(ManagedScanState.COMPLETE, progress = running(10).progress,
            terminating = true)
        val cleared = terminal.copy(terminating = false)
        assertTrue("the transition itself refreshes", LibraryRefreshPolicy.indexingChanged(running(10), terminal))
        assertFalse("the teardown must not refresh again", LibraryRefreshPolicy.indexingChanged(terminal, cleared))
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
