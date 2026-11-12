package app.enotone.android

import android.graphics.Bitmap
import app.enotone.library.AlbumTagRefreshReport
import app.enotone.library.LibraryRepository
import app.enotone.library.LibrarySnapshot
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.OfflineTrackRow
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.QueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** App-scoped restoration policy: pure fake Media3 binding and Room repository. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val email = "test@example.invalid"
    private fun id(n: Int) = n.toString(16).padStart(64, '0')
    private fun catalog(): LibrarySnapshot {
        val ids = listOf(id(1), id(2))
        return LibrarySnapshot(
            ids.map { OfflineTrackRow(id = it, title = "Track", artist = "Artist", album = "Album") },
            ids.associateWith { QueueTrack(it, "Track", "/AllMusic/${it}.mp3", 100L, null) },
        )
    }

    private class Repo(val items: LibrarySnapshot) : LibraryRepository {
        var checkpoint: PersistedQueueState? = null
        var reads = 0
        override suspend fun load(email: String) = items
        override suspend fun readSavedQueue(): PersistedQueueState? {
            reads++
            return checkpoint
        }
        override suspend fun refreshAlbum(
            email: String, password: String, ids: List<String>,
        ): AlbumTagRefreshReport = error("Not used")
    }

    private class Connection : PlaybackConnection {
        private val snapshots = MutableStateFlow(PlaybackSnapshot(connected = true))
        override val state: StateFlow<PlaybackSnapshot> = snapshots
        var connects = 0
        var disconnects = 0
        var plays = 0
        var restores = 0
        var lastRestored: PersistedQueueState? = null
        var lastFiltered = false
        override fun connect() { connects++ }
        override fun disconnect() { disconnects++ }
        override fun playQueue(email: String, password: String, queue: List<QueueTrack>,
            selectedId: String, useCache: Boolean, sourceKey: String) { plays++ }
        override fun restoreQueue(email: String, password: String, queue: List<QueueTrack>,
            saved: PersistedQueueState, useCache: Boolean, filtered: Boolean): Boolean {
            restores++
            lastRestored = saved
            lastFiltered = filtered
            snapshots.value = snapshots.value.copy(
                queueTotal = queue.size,
                currentTrackId = saved.selectedTrackId,
                title = "Track",
                playbackState = "PAUSED",
            )
            return true
        }
        override fun stopPlayback(): Boolean = true
        override fun playPause() = Unit
        override fun playNext() = Unit
        override fun playPrevious() = Unit
        override fun cycleRepeatMode() = Unit
        override fun toggleShuffle() = Unit
        override fun seekToFraction(fraction: Float) = Unit
    }

    private object NoArtwork : LibraryArtworkLoader {
        override fun peek(email: String, group: AlbumArtworkGroup, size: Int): Bitmap? = null
        override suspend fun cached(email: String, group: AlbumArtworkGroup, size: Int): Bitmap? = null
        override suspend fun fetch(email: String, password: String,
            group: AlbumArtworkGroup, size: Int): Bitmap? = null
        override suspend fun invalidate(email: String,
            targets: Collection<PlayingArtworkTarget>) = Unit
    }

    private fun saved(source: String) = PersistedQueueState(
        trackIds = listOf(id(1), id(2), id(3)),
        selectedTrackId = id(2),
        positionMs = 4_000L,
        repeatMode = 2,
        shuffleEnabled = false,
        shuffledIndices = null,
        sourceKey = source,
        savedAtEpochMs = 123L,
        queueRevision = "01234567-89ab-cdef-0123-456789abcdef",
    )

    @Test fun savedLoginRestoresOnlySurvivorsAndNeverPlaysAutomatically() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Repo(catalog())
            repo.checkpoint = saved(LibrarySnapshotPlanner.accountKey(email))
            val connection = Connection()
            val model = PlayerViewModel(connection, repo,
                CredentialsProvider { StoredWebDavLogin(email, "stored-password") },
                NoArtwork, StandardTestDispatcher(testScheduler), { true })
            advanceUntilIdle()
            assertEquals(1, connection.connects)
            assertEquals(1, connection.restores)
            assertEquals(1, repo.reads)
            assertEquals(listOf(id(1), id(2)), connection.lastRestored?.trackIds)
            assertTrue(connection.lastFiltered)
            assertEquals(0, connection.plays)
            assertEquals("PAUSED", model.state.value.playback.playbackState)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun manualRamCredentialsCannotRestoreWithoutVaultConsent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Repo(catalog())
            repo.checkpoint = saved(LibrarySnapshotPlanner.accountKey(email))
            val connection = Connection()
            val model = PlayerViewModel(connection, repo,
                CredentialsProvider { null }, NoArtwork,
                StandardTestDispatcher(testScheduler), { true })
            advanceUntilIdle()
            model.onCredentialsChanged(email, "ram-only")
            advanceUntilIdle()
            assertEquals(0, connection.restores)
            assertEquals(0, repo.reads)
            assertEquals(0, connection.plays)
            assertFalse(model.state.value.playback.queueTotal > 0)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun foreignSourceDoesNotRestoreTheQueue() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Repo(catalog())
            repo.checkpoint = saved(id(99))
            val connection = Connection()
            PlayerViewModel(connection, repo,
                CredentialsProvider { StoredWebDavLogin(email, "stored-password") },
                NoArtwork, StandardTestDispatcher(testScheduler), { true })
            advanceUntilIdle()
            assertEquals(0, connection.restores)
            assertEquals(1, repo.reads)
            assertNull(connection.lastRestored)
        } finally { Dispatchers.resetMain() }
    }
}
