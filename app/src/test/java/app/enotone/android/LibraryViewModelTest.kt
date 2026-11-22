package app.enotone.android

import android.graphics.Bitmap
import app.enotone.library.AlbumTagRefreshReport
import app.enotone.library.LibraryPage
import app.enotone.library.LibraryRepository
import app.enotone.library.LibrarySnapshot
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.OfflineTrackRow
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.QueueEditResult
import app.enotone.playback.QueueEditStatus
import app.enotone.playback.QueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val email = "test@example.invalid"
    private var randomForQueue: Random = Random.Default
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
        var inserted: List<QueueTrack> = emptyList()
        var appended: List<QueueTrack> = emptyList()
        var failQueueEdit = false
        var failPlayQueue = false
        var connectedCount = 0
        var disconnectedCount = 0
        override fun connect() { connectedCount++ }
        override fun disconnect() { disconnectedCount++ }
        override fun playQueue(
            email: String, password: String, queue: List<QueueTrack>,
            selectedId: String, useCache: Boolean, sourceKey: String,
        ) {
            check(!failPlayQueue) { "synthetic queue-start failure" }
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
        override fun insertNext(tracks: List<QueueTrack>): QueueEditResult {
            check(!failQueueEdit) { "synthetic edit failure" }
            inserted = tracks
            return QueueEditResult(QueueEditStatus.APPLIED, tracks.size)
        }
        override fun append(tracks: List<QueueTrack>): QueueEditResult {
            appended = tracks
            return QueueEditResult(QueueEditStatus.APPLIED, tracks.size)
        }
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
        queueRandom = randomForQueue,
    )

    @Test fun restoredPlayerDetailWaitsForColdCatalogueAndRejectsMissingIds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val track = row(1, "Fixture")
            val vm = model(this, FakeRepository(catalog(track)))
            val album = async(UnconfinedTestDispatcher(testScheduler)) {
                vm.playerDetail(track.id, LibraryPage.ALBUMS)
            }
            assertFalse(album.isCompleted)
            advanceUntilIdle()
            assertEquals(LibraryDetail.Album(vm.state.value.groupIndex.albums.single().key), album.await())
            assertEquals(LibraryDetail.Artist(vm.state.value.groupIndex.artists.single().key),
                vm.playerDetail(track.id, LibraryPage.ARTISTS))
            assertNull(vm.playerDetail("missing", LibraryPage.ALBUMS))
            assertNull(vm.playerDetail("missing", LibraryPage.ARTISTS))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun queueActionsStartEmptyAndRouteLaterEditsWithFeedback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val player = FakeConnection()
            val vm = model(this, FakeRepository(catalog(
                row(1, "Первая"), row(2, "Вторая"), row(3, "Третья"),
            )), player)
            advanceUntilIdle()
            val first = async(UnconfinedTestDispatcher(testScheduler)) { vm.queueMessages.first() }
            vm.onPlayNext(listOf(row(2, "Вторая"), row(3, "Третья")))
            advanceUntilIdle()
            assertEquals(id(2), player.selected)
            assertEquals(listOf(id(2), id(3)), player.queue.map { it.id })
            assertEquals(2, first.await().result.count)

            val second = async(UnconfinedTestDispatcher(testScheduler)) { vm.queueMessages.first() }
            vm.onPlayNext(listOf(row(1, "Первая")))
            advanceUntilIdle()
            assertEquals(listOf(id(1)), player.inserted.map { it.id })
            assertTrue(second.await().playNext)

            val third = async(UnconfinedTestDispatcher(testScheduler)) { vm.queueMessages.first() }
            vm.onAppendToQueue(listOf(row(3, "Третья")))
            advanceUntilIdle()
            assertEquals(listOf(id(3)), player.appended.map { it.id })
            assertFalse(third.await().playNext)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun longListStartsAtSelectedTrackCapsPlayAllAndShufflesAcrossWholeList() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val rows = (1..3_647).map { row(it, "Песня $it") }
            val snapshot = LibrarySnapshot(rows, rows.associate { item ->
                item.id to QueueTrack(item.id, item.title, "/AllMusic/${item.id}.mp3", 1234L, null)
            })
            val player = FakeConnection()
            randomForQueue = Random(192)
            val vm = model(this, FakeRepository(snapshot), player)
            advanceUntilIdle()

            vm.onTrackClicked(rows[500], rows)
            advanceUntilIdle()
            assertEquals(100, player.queue.size)
            assertEquals(rows.drop(500).take(100).map { it.id }, player.queue.map { it.id })

            vm.onTrackClicked(rows.last(), rows)
            advanceUntilIdle()
            assertEquals(listOf(rows.last().id), player.queue.map { it.id })

            vm.onPlayAll()
            advanceUntilIdle()
            assertEquals(vm.state.value.visible.take(100).map { it.id }, player.queue.map { it.id })

            val shuffleOrder = vm.state.value.visible.shuffled(Random(192))
            vm.onShuffleAll()
            advanceUntilIdle()
            assertEquals(shuffleOrder.take(100).map { it.id }, player.queue.map { it.id })
            assertEquals(100, player.queue.size)
            val firstHundred = rows.take(100).mapTo(mutableSetOf()) { it.id }
            assertTrue(player.queue.any { it.id !in firstHundred })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun appendFromEmptyQueueTakesFirstHundredSongsOfLongSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val rows = (1..101).map { row(it, "Песня $it") }
            val player = FakeConnection()
            val vm = model(this, FakeRepository(catalog(*rows.toTypedArray())), player)
            advanceUntilIdle()

            val feedback = async(UnconfinedTestDispatcher(testScheduler)) { vm.queueMessages.first() }
            vm.onAppendToQueue(rows)
            advanceUntilIdle()

            val message = feedback.await()
            assertEquals(QueueEditStatus.APPLIED, message.result.status)
            assertFalse(message.playNext)
            assertEquals(rows.take(100).map { it.id }, player.queue.map { it.id })
            assertEquals(100, player.queue.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun longContextStartsHundredSongsWithoutAQueueMessage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val rows = (1..101).map { row(it, "Песня $it") }
            val player = FakeConnection()
            val vm = model(this, FakeRepository(catalog(*rows.toTypedArray())), player)
            advanceUntilIdle()

            val message = async(UnconfinedTestDispatcher(testScheduler)) {
                vm.queueMessages.first()
            }
            vm.onTrackClicked(rows.first(), rows)
            advanceUntilIdle()

            assertEquals(100, player.queue.size)
            assertEquals(rows.take(100).map { it.id }, player.queue.map { it.id })
            assertFalse(message.isCompleted)
            message.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun recentlyAddedHomeAndAllQueuesUseTheirVisibleFiveAndHundredRows() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val rows = (1..120).map { number ->
                row(number, "Song $number").copy(firstSeenEpochMs = number.toLong())
            }
            val player = FakeConnection()
            val vm = model(this, FakeRepository(catalog(*rows.toTypedArray())), player)
            advanceUntilIdle()

            val allRecent = vm.state.value.recentlyAddedTracks
            val homeRecent = allRecent.take(RecentlyAddedPlanner.HOME_TRACKS_LIMIT)
            assertEquals(100, allRecent.size)
            assertEquals((120 downTo 21).map(::id), allRecent.map { it.id })
            assertEquals(5, homeRecent.size)

            vm.onTrackClicked(homeRecent.first(), homeRecent)
            advanceUntilIdle()
            assertEquals(homeRecent.map { it.id }, player.queue.map { it.id })

            vm.onTrackClicked(allRecent.first(), allRecent)
            advanceUntilIdle()
            assertEquals(100, player.queue.size)
            assertEquals(allRecent.map { it.id }, player.queue.map { it.id })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun searchAndIndexSurviveAlbumRoundTrip() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = model(this, FakeRepository(catalog(row(1, "Зима"), row(2, "Лето"))))
            advanceUntilIdle()
            val index = vm.state.value.groupIndex
            vm.onSearch("Зима")
            advanceUntilIdle()
            val album = index.albumByTrackId.getValue(id(1))
            vm.onRouteSelected(LibraryPage.ALBUMS, album.key)
            advanceUntilIdle()
            assertEquals("", vm.state.value.search)
            assertEquals(2, vm.state.value.albumTracks.size)
            vm.onRouteSelected(LibraryPage.TRACKS, null)
            advanceUntilIdle()
            assertEquals("Зима", vm.state.value.search)
            assertEquals(listOf(id(1)), vm.state.value.visible.map { it.id })
            assertTrue(index === vm.state.value.groupIndex)
            assertEquals(listOf(id(2), id(1)).sorted(),
                vm.state.value.artistPlans.values.single().songs.map { it.id }.sorted())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun queueEditFailureHasDistinctFeedback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val connection = FakeConnection()
            val vm = model(this, FakeRepository(catalog(row(1, "Один"), row(2, "Два"))), connection)
            advanceUntilIdle()
            vm.onPlayNext(listOf(row(1, "Один")))
            advanceUntilIdle()
            connection.failQueueEdit = true
            val feedback = async(UnconfinedTestDispatcher(testScheduler)) { vm.queueMessages.first() }
            vm.onPlayNext(listOf(row(2, "Два")))
            advanceUntilIdle()
            assertEquals(QueueEditStatus.FAILED, feedback.await().result.status)
        } finally { Dispatchers.resetMain() }
    }

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

    @Test fun trackSearchUsesPreparedMemoryKeys() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = FakeRepository(catalog(row(1, "Мальчик"), row(2, "Лето"), row(3, "Malibu")))
            val vm = model(this, repo)
            advanceUntilIdle()
            vm.onSearch("маль")
            advanceUntilIdle()
            assertEquals(listOf(id(1)), vm.state.value.visible.map { it.id })
            vm.onSearch("MAL")
            advanceUntilIdle()
            assertEquals(listOf(id(3)), vm.state.value.visible.map { it.id })
            vm.onSearch("")
            advanceUntilIdle()
            assertEquals(3, vm.state.value.visible.size)
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

    @Test fun selectedTrackPlaysOnlyItsFollowingVisibleContext() = runTest {
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
            assertEquals(listOf(id(2)), player.queue.map { it.id })
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

    @Test fun `page selection remains stable across unrelated player screen transitions`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val library = model(this, FakeRepository(catalog(row(1, "Зима"))))
            advanceUntilIdle()
            library.onPageSelected(LibraryPage.ARTISTS)
            assertEquals(LibraryPage.ARTISTS, library.state.value.section)
            // Слой «Сейчас играет» не меняет страницу медиатеки.
            advanceUntilIdle()
            assertEquals(LibraryPage.ARTISTS, library.state.value.section)
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
            library.onPageSelected(LibraryPage.ALBUMS)
            assertEquals(LibraryPage.TRACKS, library.state.value.section)
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `a tap before the service is bound asks for feedback instead of vanishing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            // Регрессия владельца: после «Выйти» → «Войти» тап по песне ничего не делал,
            // пока пользователь не поставит песню в очередь. Тап обязан давать отклик и
            // просить переподключение, а не молчать.
            val connection = FakeConnection()
            val library = model(this, FakeRepository(catalog(row(1, "A"))), player = connection)
            advanceUntilIdle()
            val requests = mutableListOf<Unit>()
            val collection = launch { library.playbackNotReady.collect { requests += it } }
            advanceUntilIdle()

            library.onPlaybackNotReady()
            advanceUntilIdle()

            assertEquals("тап без соединения должен давать отклик", 1, requests.size)
            collection.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `playback feedback is suppressed while the account is signing out`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val connection = FakeConnection()
            val events = MutableStateFlow(AccountState())
            val library = model(this, FakeRepository(catalog(row(1, "A"))),
                player = connection, events = events)
            advanceUntilIdle()
            val requests = mutableListOf<Unit>()
            val collection = launch { library.playbackNotReady.collect { requests += it } }
            advanceUntilIdle()

            // Во время выхода данные аккаунта стираются — сообщений быть не должно.
            events.value = AccountState(
                sourceKey = LibrarySnapshotPlanner.accountKey(email),
                phase = AccountPhase.SIGNING_OUT,
            )
            advanceUntilIdle()
            library.onPlaybackNotReady()
            advanceUntilIdle()

            assertEquals(0, requests.size)
            collection.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
