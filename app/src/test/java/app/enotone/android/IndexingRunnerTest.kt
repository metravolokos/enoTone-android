package app.enotone.android

import app.enotone.library.ManagedIndexProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IndexingRunnerTest {
    private val request = PendingIndexRun(
        StoredWebDavLogin("synthetic@example.test", "synthetic-secret"),
    )

    @Before fun reset() {
        ManagedScanRuntime.status.value = ManagedScanStatus(ManagedScanState.RUNNING)
    }

    @After fun clear() {
        ManagedScanRuntime.status.value = ManagedScanStatus()
    }

    private fun progress(saved: Int) = ManagedIndexProgress(
        completedBatches = 1,
        attempted = saved,
        savedWithTags = saved,
        completedWithoutTags = 0,
        retryableErrors = 0,
        permanentErrors = 0,
        staleWrites = 0,
        getRequests = saved,
        downloadedBytes = saved * 1024L,
        remaining = 0,
        stopCode = "NO_ELIGIBLE_FILES",
    )

    private fun TestScope.runner(
        open: suspend () -> IndexingSessionPort,
        signOut: () -> Boolean = { false },
    ) = IndexingRunner(
        sessions = IndexingSessionFactory { open() },
        signOutPending = signOut,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    @Test fun completesAndPublishesCheckpoint() = runTest {
        var opens = 0
        val runner = runner({
            opens++
            IndexingSessionPort { _, callback ->
                callback(progress(3))
                progress(3)
            }
        })
        assertTrue(runner.run(request))
        assertEquals(1, opens)
        assertEquals(ManagedScanState.COMPLETE, ManagedScanRuntime.status.value.state)
        assertEquals(3, ManagedScanRuntime.status.value.progress?.savedWithTags)
        assertTrue(ManagedScanRuntime.status.value.terminating)
        runner.onDestroy()
        assertFalse(ManagedScanRuntime.status.value.terminating)
    }

    @Test fun pauseAndResumeStartsAtPersistedCheckpoint() = runTest {
        val held = CompletableDeferred<Unit>()
        var checkpoint = 0
        val first = runner({
            IndexingSessionPort { _, callback ->
                checkpoint = 1
                callback(progress(checkpoint))
                held.await()
                progress(checkpoint)
            }
        })
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { first.run(request) }
        first.pause(jobActive = true)
        job.cancelAndJoin()
        assertEquals(ManagedScanState.PAUSED, ManagedScanRuntime.status.value.state)
        assertEquals("PAUSED", ManagedScanRuntime.status.value.progress?.stopCode)
        first.onDestroy()

        // The fake checkpoint stands in for durable Room rows; no file is repeated.
        val previous = ManagedScanRuntime.status.value.progress
        ManagedScanRuntime.status.value = ManagedScanStatus(ManagedScanState.RUNNING, previous)
        val resumed = runner({
            IndexingSessionPort { _, callback ->
                checkpoint++
                callback(progress(checkpoint))
                progress(checkpoint)
            }
        })
        assertTrue(resumed.run(request))
        assertEquals(2, checkpoint)
        assertEquals(ManagedScanState.COMPLETE, ManagedScanRuntime.status.value.state)
    }

    @Test fun stopCancelsWorkWithoutLosingProgress() = runTest {
        val held = CompletableDeferred<Unit>()
        val runner = runner({
            IndexingSessionPort { _, callback ->
                callback(progress(1))
                held.await()
                progress(2)
            }
        })
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { runner.run(request) }
        runner.stop(jobActive = true)
        job.cancelAndJoin()
        assertEquals(ManagedScanState.STOPPED, ManagedScanRuntime.status.value.state)
        assertEquals(1, ManagedScanRuntime.status.value.progress?.savedWithTags)
        assertEquals("STOPPED", ManagedScanRuntime.status.value.progress?.stopCode)
    }

    @Test fun errorCodesNeverExposeExceptionMessagesOrPaths() = runTest {
        val openFailure = runner({ throw java.io.IOException("/AllMusic/private.mp3 password") })
        assertFalse(openFailure.run(request))
        assertEquals("DATABASE_OPEN", ManagedScanRuntime.status.value.failureStage)
        assertEquals("IO", ManagedScanRuntime.status.value.failureReason)
        assertFalse(ManagedScanRuntime.status.value.toString().contains("private.mp3"))

        ManagedScanRuntime.status.value = ManagedScanStatus(ManagedScanState.RUNNING)
        val batchFailure = runner({
            IndexingSessionPort { _, _ -> throw IllegalArgumentException("secret/path") }
        })
        assertFalse(batchFailure.run(request))
        assertEquals("INDEX_BATCH", ManagedScanRuntime.status.value.failureStage)
        assertEquals("ARGUMENT", ManagedScanRuntime.status.value.failureReason)
        assertFalse(ManagedScanRuntime.status.value.toString().contains("secret/path"))
    }

    @Test fun timeoutPausesWithLatestProgress() = runTest {
        val held = CompletableDeferred<Unit>()
        val runner = runner({
            IndexingSessionPort { _, callback ->
                callback(progress(7))
                held.await()
                progress(7)
            }
        })
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { runner.run(request) }
        runner.onTimeout(jobActive = true)
        job.cancelAndJoin()
        assertEquals(ManagedScanState.PAUSED, ManagedScanRuntime.status.value.state)
        assertEquals(7, ManagedScanRuntime.status.value.progress?.savedWithTags)
        assertEquals("PAUSED", ManagedScanRuntime.status.value.progress?.stopCode)
    }

    @Test fun secondStartWhileRunningCannotOpenAnotherSession() = runTest {
        val held = CompletableDeferred<Unit>()
        var opens = 0
        val runner = runner({
            opens++
            IndexingSessionPort { _, _ ->
                held.await()
                progress(1)
            }
        })
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { runner.run(request) }
        assertFalse(runner.run(request))
        assertEquals(1, opens)
        runner.stop(jobActive = true)
        job.cancelAndJoin()
    }

    @Test fun pendingSignOutNeverStartsNetworkOrDatabase() = runTest {
        var opens = 0
        val runner = runner({
            opens++
            IndexingSessionPort { _, _ -> progress(1) }
        }, signOut = { true })
        assertFalse(runner.run(request))
        assertEquals(0, opens)
        assertEquals(ManagedScanState.PAUSED, ManagedScanRuntime.status.value.state)
    }
}
