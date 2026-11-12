package app.enotone.android

import android.graphics.Bitmap
import app.enotone.library.AlbumTagRefreshReport
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.LibraryPage
import app.enotone.library.LibraryRepository
import app.enotone.library.LibrarySnapshot
import app.enotone.library.OfflineTrackRow
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.QueueTrack
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val email = "test@example.invalid"
    private fun id(n: Int) = n.toString(16).padStart(64, '0')
    private fun row(n: Int, title: String) =
        OfflineTrackRow(id = id(n), title = title, artist = "Исполнитель",
            album = "Альбом", hasStoredTagData = true)
    private fun catalog(vararg tracks: OfflineTrackRow): LibrarySnapshot =
        LibrarySnapshot(tracks.toList(), tracks.associate { row ->
            row.id to QueueTrack(row.id, row.title,
                "/AllMusic/${row.id}.mp3", 1234L, null)
        })

    private class FakeRepository(var response: LibrarySnapshot) : LibraryRepository {
        var failure: Boolean = false
        var saved: PersistedQueueState? = null
        var reads = 0
        override suspend fun load(email: String): LibrarySnapshot {
            if (failure) error("synthetic failure")
            return response
        }
        override suspend fun readSavedQueue(): PersistedQueueState? {
            reads++
            return saved
        }
        override suspend fun refreshAlbum(
            email: String, password: String, ids: List<String>,
        ): AlbumTagRefreshReport = error("Not used")
    }

    private class FakeConnection : PlaybackConnection {
        private val current = MutableStateFlow(PlaybackSnapshot(connected = true))
        override val state: StateFlow<PlaybackSnapshot> = current
        var selected: String? = null
        var queue: List<QueueTrack> = emptyList()
        var restored: PersistedQueueState? = null
        var filtered: Boolean? = null
        var connectedCount = 0
        var disconnectedCount = 0
        override fun connect() { connectedCount++ }
        override fun disconnect() { disconnectedCount++ }
        override fun playQueue(
            email: String, password: String, queue: List<QueueTrack>,
            selectedId: String, useCache: Boolean, sourceKey: String,
        ) {
            this.queue = queue
            selected = selectedId
            current.value = current.value.copy(queueTotal = queue.size,
                queuePosition = queue.indexOfFirst { it.id == selectedId } + 1,
                currentTrackId = selectedId)
        }
        override fun restoreQueue(
            email: String, password: String, queue: List<QueueTrack>,
            saved: PersistedQueueState, useCache: Boolean, filtered: Boolean,
        ): Boolean {
            restored = saved
            this.filtered = filtered
            this.queue = queue
            current.value = current.value.copy(queueTotal = queue.size,
                currentTrackId = saved.selectedTrackId,
                positionMs = saved.positionMs,
                repeatMode = saved.repeatMode,
                shuffleEnabled = saved.shuffleEnabled)
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
        override suspend fun fetch(
            email: String, password: String, group: AlbumArtworkGroup, size: Int,
        ): Bitmap? = null
        override suspend fun invalidate(
            email: String, targets: Collection<PlayingArtworkTarget>,
        ) = Unit
    }

    private fun model(
        scope: TestScope,
        repository: FakeRepository,
        player: FakeConnection = FakeConnection(),
        events: StateFlow<AccountState> = MutableStateFlow(AccountState()),
        revision: StateFlow<Long> = MutableStateFlow(0L),
        pages: List<LibraryPage> = LibraryPage.entries.toList(),
    ): LibraryViewModel = LibraryViewModel(
        accountEmail = email,
        password = "RAM-only",
        initialPage = LibraryPage.TRACKS,
        repository = repository,
        connection = player,
        artwork = NoArtwork,
        io = StandardTestDispatcher(scope.testScheduler),
        unknownAlbumName = "Неизвестный альбом",
        messages = AlbumRefreshMessages(),
        clockMs = { 1234L },
        managedStatus = MutableStateFlow(ManagedScanStatus()),
        accountEvents = events,
        dataRevision = revision,
        visiblePages = pages,
    )

    @Test fun loadDataAndEmptyLibrary() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val data = model(this, FakeRepository(catalog(row(1, "Зима"))))
            advanceUntilIdle()
            assertFalse(data.state.value.loading)
            assertFalse(data.state.value.failed)
            assertEquals(1, data.state.value.tracks.size)
            assertEquals(1, data.state.value.visible.size)

            val empty = model(this, FakeRepository(catalog()))
            advanceUntilIdle()
            assertFalse(empty.state.value.loading)
            assertFalse(empty.state.value.failed)
            assertTrue(empty.state.value.tracks.isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun repositoryExceptionProducesErrorState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = FakeRepository(catalog())
            repo.failure = true
            val vm = model(this, repo)
            advanceUntilIdle()
            assertTrue(vm.state.value.failed)
            assertFalse(vm.state.value.loading)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun searchIsCaseInsensitiveAndResetRestoresResults() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = model(this, FakeRepository(catalog(
                row(1, "Зимняя дорога"), row(2, "Летний дождь"),
            )))
            advanceUntilIdle()
            vm.onSearch("ЗИМНЯЯ")
            advanceUntilIdle()
            assertEquals(listOf(id(1)), vm.state.value.visible.map { it.id })
            vm.onSearch("")
            advanceUntilIdle()
            assertEquals(2, vm.state.value.visible.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun pageChangeAndGroupNavigationRetainLibrary() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = model(this, FakeRepository(catalog(row(1, "Песня"))))
            advanceUntilIdle()
            vm.onPageSelected(LibraryPage.ALBUMS)
            advanceUntilIdle()
            assertEquals(LibraryPage.ALBUMS, vm.state.value.section)
            val group = vm.state.value.groups.single()
            vm.onGroupSelected(group.key)
            advanceUntilIdle()
            assertEquals(group.key, vm.state.value.selectedGroupKey)
            assertEquals(1, vm.state.value.visible.size)
            vm.onPageSelected(LibraryPage.ARTISTS)
            advanceUntilIdle()
            assertNull(vm.state.value.selectedGroupKey)
            assertEquals(LibraryPage.ARTISTS, vm.state.value.section)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun selectedTrackPlaysExactVisibleQueueContext() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val player = FakeConnection()
            val vm = model(this, FakeRepository(catalog(
                row(1, "А"), row(2, "Б"), row(3, "В"),
            )), player)
            advanceUntilIdle()
            val selected = vm.state.value.visible.take(2)
            vm.onTrackClicked(selected[1], selected)
            advanceUntilIdle()
            assertEquals(id(2), player.selected)
            assertEquals(listOf(id(1), id(2)), player.queue.map { it.id })
        } finally { Dispatchers.resetMain() }
    }

    @Test fun libraryNeverInitiatesQueueRestoration() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = FakeRepository(catalog(row(1, "А"), row(2, "Б")))
            val player = FakeConnection()
            val vm = model(this, repo, player)
            advanceUntilIdle()
            assertFalse(vm.state.value.loading)
            assertEquals(0, repo.reads)
            assertNull(player.restored)
            // The Activity-scoped PlayerViewModel is the only service binder.
            assertEquals(0, player.connectedCount)
            assertEquals(0, player.disconnectedCount)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun signOutImmediatelyClearsActivityScopedLibraryAndBlocksStaleQueries() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = AccountSession()
            val original = row(1, "Зима")
            val repo = FakeRepository(catalog(original, row(2, "Весна")))
            val connection = FakeConnection()
            val vm = model(this, repo, connection, session.state)
            advanceUntilIdle()
            assertEquals(2, vm.state.value.tracks.size)
            assertEquals(2, vm.state.value.visible.size)
            val key = LibrarySnapshotPlanner.accountKey(email)

            session.beginSignOut(key)
            advanceUntilIdle()
            assertFalse(vm.state.value.loading)
            assertTrue(vm.state.value.tracks.isEmpty())
            assertTrue(vm.state.value.visible.isEmpty())
            assertTrue(vm.state.value.artworkCatalog.groups.isEmpty())
            assertNull(vm.state.value.currentTrackId)

            session.finishSignOut(key)
            advanceUntilIdle()
            vm.onSearch("Зима")
            vm.onTrackClicked(original, listOf(original))
            advanceUntilIdle()
            assertTrue(vm.state.value.tracks.isEmpty())
            assertTrue(vm.state.value.visible.isEmpty())
            assertNull(connection.selected) // no stale playback after account deletion
        } finally { Dispatchers.resetMain() }
    }

    @Test fun suspendedRoomLoadCannotRepopulateAfterSignOut() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = AccountSession()
            val key = LibrarySnapshotPlanner.accountKey(email)
            session.finishSignOut(key)
            val vm = model(this, FakeRepository(catalog(row(1, "Старый трек"))),
                events = session.state)
            advanceUntilIdle()
            assertTrue(vm.state.value.tracks.isEmpty())
            assertTrue(vm.state.value.visible.isEmpty())
            assertFalse(vm.state.value.loading)
        } finally { Dispatchers.resetMain() }
    }


    @Test fun `data revision reloads an open library without recreating it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeRepository(catalog(row(1, "Зима")))
            val revision = MutableStateFlow(0L)
            val library = model(this, repository, revision = revision)
            advanceUntilIdle()
            assertEquals(1, library.state.value.tracks.size)
            repository.response = catalog(row(1, "Зима"), row(2, "Весна"))
            revision.value = 1L
            advanceUntilIdle()
            assertEquals(2, library.state.value.tracks.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `new open request switches page but the same request keeps user choice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val library = model(this, FakeRepository(catalog(row(1, "Зима"))))
            advanceUntilIdle()
            library.onOpenRequest(1, LibraryPage.TRACKS)
            library.onPageSelected(LibraryPage.ARTISTS)
            // Возврат из «Сейчас играет»: тот же запрос — выбор пользователя сохраняется (O-13).
            library.onOpenRequest(1, LibraryPage.TRACKS)
            assertEquals(LibraryPage.ARTISTS, library.state.value.section)
            // «Домой → Альбомы»: новый запрос открывает выбранную страницу.
            library.onOpenRequest(2, LibraryPage.ALBUMS)
            assertEquals(LibraryPage.ALBUMS, library.state.value.section)
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `hiding the current page moves the open library to a visible one`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val library = model(this, FakeRepository(catalog(row(1, "Зима"))))
            advanceUntilIdle()
            library.onPageSelected(LibraryPage.ALBUMS)
            library.onVisiblePagesChanged(listOf(LibraryPage.TRACKS, LibraryPage.ARTISTS))
            assertEquals(listOf(LibraryPage.TRACKS, LibraryPage.ARTISTS), library.state.value.visiblePages)
            assertEquals(LibraryPage.TRACKS, library.state.value.section)
            library.onOpenRequest(1, LibraryPage.TRACKS)
            library.onOpenRequest(2, LibraryPage.ALBUMS)
            assertEquals(LibraryPage.TRACKS, library.state.value.section)
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
