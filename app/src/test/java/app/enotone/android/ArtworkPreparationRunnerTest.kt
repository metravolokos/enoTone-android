package app.enotone.android

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
class ArtworkPreparationRunnerTest {
    private val login = StoredWebDavLogin("synthetic@example.test", "synthetic-secret")

    private class FakeArtworkPort(
        var albumIds: List<Int> = listOf(1, 2, 3),
    ) : ArtworkPreparationPort<Int> {
        val indexed = mutableSetOf<Int>()
        val requested = mutableListOf<Int>()
        val notifications = mutableListOf<Pair<Int, Int>>()
        var tagsActive = false
        var onPrepare: suspend (Int) -> Unit = { indexed.add(it) }

        override suspend fun groups(login: StoredWebDavLogin) = albumIds
        override suspend fun isIndexed(login: StoredWebDavLogin, group: Int) = group in indexed
        override suspend fun prepare(login: StoredWebDavLogin, group: Int) {
            requested.add(group)
            onPrepare(group)
        }
        override fun tagsRunning() = tagsActive
        override suspend fun publish(completed: Int, total: Int) {
            notifications.add(completed to total)
        }
    }

    @Before fun reset() {
        ArtworkScanRuntime.status.value = ArtworkScanStatus(ArtworkScanState.RUNNING)
    }

    @After fun clear() {
        ArtworkScanRuntime.status.value = ArtworkScanStatus()
    }

    private fun TestScope.runner(
        port: FakeArtworkPort,
        sleep: suspend (Long) -> Unit = {},
        signOut: () -> Boolean = { false },
    ) = ArtworkPreparationRunner(
        port = port,
        signOutPending = signOut,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
        sleep = sleep,
    )

    @Test fun completesEveryAlbumAndPublishesCounts() = runTest {
        val port = FakeArtworkPort()
        val runner = runner(port)
        assertTrue(runner.run(login))
        assertEquals(listOf(1, 2, 3), port.requested)
        assertEquals(3, ArtworkScanRuntime.status.value.completed)
        assertEquals(3, ArtworkScanRuntime.status.value.total)
        assertEquals(ArtworkScanState.COMPLETE, ArtworkScanRuntime.status.value.state)
        assertEquals(listOf(3 to 3), port.notifications)
    }

    @Test fun noArtCheckpointSkipsDownloadOnRepeatedRuns() = runTest {
        val port = FakeArtworkPort(listOf(7))
        // The permanent NO_ART reference counts as indexed by the real repository.
        port.indexed.add(7)
        assertTrue(runner(port).run(login))
        assertTrue(port.requested.isEmpty())
        ArtworkScanRuntime.status.value = ArtworkScanStatus(ArtworkScanState.RUNNING)
        assertTrue(runner(port).run(login))
        assertTrue(port.requested.isEmpty())
    }

    @Test fun pauseAndStopPreserveCounts() = runTest {
        val held = CompletableDeferred<Unit>()
        val port = FakeArtworkPort(listOf(1, 2))
        port.onPrepare = { group ->
            if (group == 2) held.await()
            port.indexed.add(group)
        }
        val runner = runner(port)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { runner.run(login) }
        runner.pause(jobActive = true)
        job.cancelAndJoin()
        assertEquals(ArtworkScanState.PAUSED, ArtworkScanRuntime.status.value.state)
        assertEquals(1, ArtworkScanRuntime.status.value.completed)
        runner.onDestroy()

        ArtworkScanRuntime.status.value = ArtworkScanStatus(ArtworkScanState.RUNNING)
        val second = runner(port)
        val other = launch(UnconfinedTestDispatcher(testScheduler)) { second.run(login) }
        second.stop(jobActive = true)
        other.cancelAndJoin()
        assertEquals(ArtworkScanState.STOPPED, ArtworkScanRuntime.status.value.state)
        assertEquals(1, ArtworkScanRuntime.status.value.completed)
    }

    @Test fun waitsForTagScanBeforeAnyArtworkRequest() = runTest {
        val port = FakeArtworkPort(listOf(1))
        port.tagsActive = true
        var waiting = 0
        val runner = runner(port, sleep = {
            if (port.tagsActive) {
                assertTrue(port.requested.isEmpty())
                waiting++
                port.tagsActive = false
            }
        })
        assertTrue(runner.run(login))
        assertEquals(1, waiting)
        assertEquals(listOf(1), port.requested)
        assertEquals(ArtworkScanState.COMPLETE, ArtworkScanRuntime.status.value.state)
    }

    @Test fun timeoutAndSignOutNeverContinueUnprotectedWork() = runTest {
        val port = FakeArtworkPort(listOf(1))
        assertFalse(runner(port, signOut = { true }).run(login))
        assertTrue(port.requested.isEmpty())
        assertEquals(ArtworkScanState.PAUSED, ArtworkScanRuntime.status.value.state)

        ArtworkScanRuntime.status.value = ArtworkScanStatus(ArtworkScanState.RUNNING)
        val held = CompletableDeferred<Unit>()
        port.onPrepare = { held.await() }
        val runner = runner(port)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { runner.run(login) }
        runner.onTimeout(jobActive = true)
        job.cancelAndJoin()
        assertEquals(ArtworkScanState.PAUSED, ArtworkScanRuntime.status.value.state)
    }
}
