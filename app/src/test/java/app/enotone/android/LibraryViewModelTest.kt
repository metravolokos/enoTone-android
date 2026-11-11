package app.enotone.android

import android.graphics.Bitmap
import app.enotone.library.AlbumTagRefreshReport
import app.enotone.library.LibraryPage
import app.enotone.library.LibraryRepository
import app.enotone.library.LibrarySnapshot
import app.enotone.library.LibrarySnapshotPlanner
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
        savedLogin: Boolean = false,
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
        credentialsSaved = { _, _ -> savedLogin },
        clockMs = { 1234L },
        managedStatus = MutableStateFlow(ManagedScanStatus()),
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

    private fun saved(ids: List<String>, source: String) = PersistedQueueState(
        trackIds = ids,
        selectedTrackId = ids[1],
        positionMs = 12_000L,
        repeatMode = 2,
        shuffleEnabled = false,
        shuffledIndices = null,
        sourceKey = source,
        savedAtEpochMs = 123_456L,
        queueRevision = "01234567-89ab-cdef-0123-456789abcdef",
    )

    @Test fun queueRequiresOptInCredentialsAndMatchingSource() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val ids = listOf(id(1), id(2))
            val repo = FakeRepository(catalog(row(1, "А"), row(2, "Б")))
            repo.saved = saved(ids, LibrarySnapshotPlanner.accountKey(email))
            val disconnected = FakeConnection()
            model(this, repo, disconnected, savedLogin = false)
            advanceUntilIdle()
            assertNull(disconnected.restored)
            val foreign = FakeConnection()
            repo.saved = saved(ids, id(99))
            model(this, repo, foreign, savedLogin = true)
            advanceUntilIdle()
            assertNull(foreign.restored)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun missingTracksAreRemovedBeforeRestoreAndDoNotTriggerPlay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val ids = listOf(id(1), id(2), id(3))
            val repo = FakeRepository(catalog(row(1, "А"), row(2, "Б")))
            repo.saved = saved(ids, LibrarySnapshotPlanner.accountKey(email))
            val player = FakeConnection()
            val vm = model(this, repo, player, savedLogin = true)
            advanceUntilIdle()
            assertEquals(listOf(id(1), id(2)), player.restored?.trackIds)
            assertEquals(listOf(id(1), id(2)), player.queue.map { it.id })
            assertTrue(player.filtered == true)
            assertNull(player.selected) // Restoration never starts a network stream.
            assertEquals(id(2), vm.state.value.currentTrackId)
            assertEquals(12_000L, vm.state.value.positionMs)
        } finally { Dispatchers.resetMain() }
    }
}
