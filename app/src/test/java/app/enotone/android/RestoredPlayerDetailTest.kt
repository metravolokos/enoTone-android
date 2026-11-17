package app.enotone.android

import android.graphics.Bitmap
import app.enotone.library.AlbumTagRefreshReport
import app.enotone.library.LibraryPage
import app.enotone.library.LibraryRepository
import app.enotone.library.LibrarySnapshot
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.OfflineTrackRow
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.QueueStateFileStore
import app.enotone.playback.QueueTrack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real checkpoint bytes and application ViewModels; only the MediaSession binder is faked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class RestoredPlayerDetailTest {
    @get:Rule val temporary = TemporaryFolder()
    private val email = "fixture@example.invalid"
    private fun id(number: Int) = number.toString(16).padStart(64, '0')

    private fun saved(source: String) = PersistedQueueState(
        listOf(id(1), id(2)), id(2), 4000L, 0, false, null, source, 123L,
        "01234567-89ab-cdef-0123-456789abcdef",
    )

    private fun catalog(): LibrarySnapshot {
        val tracks = (1..2).map { OfflineTrackRow(id(it), "Fixture", "Artist", "Album") }
        return LibrarySnapshot(tracks, tracks.associate { row -> row.id to
            QueueTrack(row.id, row.title, "/AllMusic/${row.id}.mp3", 100L, null) })
    }

    private class Repo(private val snapshot: LibrarySnapshot) : LibraryRepository {
        var checkpoint: PersistedQueueState? = null
        override suspend fun load(email: String) = snapshot
        override suspend fun readSavedQueue() = checkpoint
        override suspend fun refreshAlbum(email: String, password: String, ids: List<String>):
            AlbumTagRefreshReport = error("Unexpected network work")
    }

    private class Connection : PlaybackConnection {
        private val mutable = MutableStateFlow(PlaybackSnapshot(connected = true))
        override val state: StateFlow<PlaybackSnapshot> = mutable
        var restores = 0
        override fun restoreQueue(email: String, password: String, queue: List<QueueTrack>,
            saved: PersistedQueueState, useCache: Boolean, filtered: Boolean): Boolean {
            restores++
            mutable.value = mutable.value.copy(queueTotal = queue.size, currentTrackId = saved.selectedTrackId,
                sourceKey = saved.sourceKey, positionMs = saved.positionMs, playbackState = "PAUSED")
            return true
        }
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun playQueue(email: String, password: String, queue: List<QueueTrack>,
            selectedId: String, useCache: Boolean, sourceKey: String): Unit = error("Autoplay forbidden")
        override fun stopPlayback() = true
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
        override suspend fun fetch(email: String, password: String, group: AlbumArtworkGroup, size: Int): Bitmap? = null
        override suspend fun invalidate(email: String, targets: Collection<PlayingArtworkTarget>) = Unit
    }

    private fun repositoryAfterRestart(): Repo {
        val directory = temporary.newFolder()
        val store = QueueStateFileStore.inDirectory(directory)
        val beforeRestart = saved(LibrarySnapshotPlanner.accountKey(email))
        store.writeLayoutAndPosition(beforeRestart.layout(), beforeRestart.position())
        val afterRestart = QueueStateFileStore.inDirectory(directory)
        return Repo(catalog()).apply { checkpoint = afterRestart.read() }
    }

    @Test fun diskRestoredPausedTrackNavigatesWhileLibraryCatalogueIsStillLoading() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = repositoryAfterRestart()
            val connection = Connection()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val player = PlayerViewModel(connection, repository,
                CredentialsProvider { StoredWebDavLogin(email, "fixture") }, NoArtwork, dispatcher, { true })
            val ready = CompletableDeferred<Unit>()
            val waitingRepository = object : LibraryRepository by repository {
                override suspend fun load(email: String): LibrarySnapshot {
                    ready.await()
                    return repository.load(email)
                }
            }
            val library = LibraryViewModel(email, "fixture", LibraryPage.TRACKS,
                waitingRepository, connection, NoArtwork, dispatcher, "Unknown", AlbumRefreshMessages(),
                managedStatus = MutableStateFlow(ManagedScanStatus()),
                dataRevision = MutableStateFlow(0L))
            advanceUntilIdle()
            assertEquals(1, connection.restores)
            assertEquals("PAUSED", player.state.value.playback.playbackState)
            assertTrue(library.state.value.loading)
            player.onNowPlaying(true)
            var nav = AppNavState()
            val opener = PlayerDetailOpener(library::playerDetail,
                { player.state.value.playback.currentTrackId },
                {
                    nav = AppNavigator.push(nav, it)
                    nav.details.lastOrNull() == it
                },
                { player.onNowPlaying(false) }, { error("Destination unavailable") })
            val request = async { opener.open(id(2), LibraryPage.ALBUMS) }
            runCurrent()
            assertFalse(request.isCompleted)
            assertTrue(player.state.value.nowPlaying)
            ready.complete(Unit)
            advanceUntilIdle()
            request.await()
            assertEquals(LibraryDetail.Album(library.state.value.groupIndex.albums.single().key),
                nav.details.single())
            assertFalse(player.state.value.nowPlaying)
            nav = requireNotNull(AppNavigator.back(nav))
            assertEquals(AppNavState(), nav)
            player.onNowPlaying(true)
            opener.open(id(2), LibraryPage.ARTISTS)
            assertEquals(LibraryDetail.Artist(library.state.value.groupIndex.artists.single().key),
                nav.details.single())
            assertFalse(player.state.value.nowPlaying)
            assertEquals(4000L, player.state.value.playback.positionMs)
        } finally { Dispatchers.resetMain() }
    }
}
