package app.enotone.android

import android.graphics.Bitmap
import androidx.media3.common.Player
import app.enotone.library.AlbumTagRefreshReport
import app.enotone.library.LibraryRepository
import app.enotone.library.LibrarySnapshot
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.OfflineTrackRow
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.PlaybackModes
import app.enotone.playback.QueuePersistenceDiagnostics
import app.enotone.playback.QueueRestoreCode
import app.enotone.playback.QueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** App-scoped restoration policy: pure fake Media3 binding and Room repository. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val email = "test@example.invalid"
    private val otherEmail = "second@example.invalid"
    private fun id(n: Int) = n.toString(16).padStart(64, '0')
    private fun catalog(): LibrarySnapshot {
        val ids = listOf(id(1), id(2))
        return LibrarySnapshot(
            ids.map { OfflineTrackRow(id = it, title = "Track", artist = "Artist", album = "Album") },
            ids.associateWith { QueueTrack(it, "Track", "/AllMusic/${it}.mp3", 100L, null) },
        )
    }

    private class Repo(items: LibrarySnapshot) : LibraryRepository {
        /** Каталог можно заменить: импорт после непригодной медиатеки возвращает треки. */
        var items: LibrarySnapshot = items
        var checkpoint: PersistedQueueState? = null
        var reads = 0

        /** Первые N чтений падают — моделирует транзиентный сбой. */
        var failReads = 0

        /** Выполняется внутри чтения: позволяет подсунуть событие, пока раунд ещё в полёте. */
        var duringRead: (() -> Unit)? = null
        val durationSaves = mutableListOf<Triple<String, String, Long>>()
        override suspend fun load(email: String) = items
        override suspend fun readSavedQueue(): PersistedQueueState? {
            reads++
            duringRead?.let { hook ->
                duringRead = null
                hook()
            }
            if (failReads > 0) {
                failReads -= 1
                error("transient read failure")
            }
            return checkpoint
        }
        override suspend fun refreshAlbum(
            email: String, password: String, ids: List<String>,
        ): AlbumTagRefreshReport = error("Not used")
        override suspend fun saveDurationIfUnknown(
            sourceKey: String, trackId: String, durationMs: Long,
        ): Boolean {
            durationSaves += Triple(sourceKey, trackId, durationMs)
            return true
        }
    }

    private class Connection : PlaybackConnection {
        private val snapshots = MutableStateFlow(PlaybackSnapshot(connected = true))
        override val state: StateFlow<PlaybackSnapshot> = snapshots
        var connects = 0
        var disconnects = 0
        var nowPlayingVisibleValue = false
        var plays = 0
        var retries = 0
        var restores = 0
        var lastRestored: PersistedQueueState? = null
        var lastQueue: List<QueueTrack> = emptyList()
        var lastFiltered = false
        fun emit(snapshot: PlaybackSnapshot) { snapshots.value = snapshot }
        override fun connect() { connects++ }
        override fun disconnect() { disconnects++ }
        override fun setNowPlayingVisible(visible: Boolean) { nowPlayingVisibleValue = visible }
        override fun playQueue(email: String, password: String, queue: List<QueueTrack>,
            selectedId: String, useCache: Boolean, sourceKey: String) { plays++ }
        override fun restoreQueue(email: String, password: String, queue: List<QueueTrack>,
            saved: PersistedQueueState, useCache: Boolean, filtered: Boolean): Boolean {
            restores++
            lastRestored = saved
            lastQueue = queue
            lastFiltered = filtered
            snapshots.value = snapshots.value.copy(
                queueTotal = queue.size,
                currentTrackId = saved.selectedTrackId,
                sourceKey = saved.sourceKey,
                durationMs = saved.durationMs,
                title = "Track",
                playbackState = "PAUSED",
            )
            return true
        }
        override fun stopPlayback(): Boolean = true
        override fun playPause() = Unit
        override fun retryCurrent(): Boolean { retries++; return true }
        override fun playNext() = Unit
        override fun playPrevious() = Unit
        override fun cycleRepeatMode() {
            snapshots.value = snapshots.value.copy(
                repeatMode = PlaybackModes.nextRepeat(snapshots.value.repeatMode))
        }
        override fun toggleShuffle() {
            snapshots.value = snapshots.value.copy(
                shuffleEnabled = !snapshots.value.shuffleEnabled)
        }
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

    @Test fun repeatAndShuffleCommandsReachSharedPlayerUiState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val connection = Connection()
            val model = PlayerViewModel(
                connection, Repo(catalog()), CredentialsProvider { null }, NoArtwork,
                StandardTestDispatcher(testScheduler), { true })
            advanceUntilIdle()
            connection.emit(PlaybackSnapshot(connected = true, queueTotal = 3))
            advanceUntilIdle()
            for (expected in listOf(
                Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_OFF,
            )) {
                model.onRepeat()
                advanceUntilIdle()
                assertEquals(expected, model.state.value.playback.repeatMode)
            }
            model.onShuffle()
            advanceUntilIdle()
            assertTrue(model.state.value.playback.shuffleEnabled)
            model.onShuffle()
            advanceUntilIdle()
            assertFalse(model.state.value.playback.shuffleEnabled)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun retryCurrentDelegatesToTheDedicatedConnectionCommand() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val connection = Connection()
            val model = PlayerViewModel(
                connection, Repo(catalog()), CredentialsProvider { null }, NoArtwork,
                StandardTestDispatcher(testScheduler), { true })
            advanceUntilIdle()

            assertTrue(model.onRetryCurrent())
            assertEquals(1, connection.retries)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun bufferingVisibilityFollowsExpandedNowPlayingWithAQueue() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val connection = Connection()
            val model = PlayerViewModel(
                connection, Repo(catalog()), CredentialsProvider { null }, NoArtwork,
                StandardTestDispatcher(testScheduler), { true })
            advanceUntilIdle()
            model.onHostResumed(true)
            model.onNowPlaying(true)
            assertFalse(connection.nowPlayingVisibleValue)

            connection.emit(PlaybackSnapshot(connected = true, queueTotal = 3))
            advanceUntilIdle()
            assertTrue(connection.nowPlayingVisibleValue)

            model.onHostResumed(false)
            assertFalse(connection.nowPlayingVisibleValue)
            model.onHostResumed(true)
            assertTrue(connection.nowPlayingVisibleValue)

            model.onNowPlaying(false)
            assertFalse(connection.nowPlayingVisibleValue)
        } finally { Dispatchers.resetMain() }
    }

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

    @Test fun failedRestoreIsRetriedInsteadOfBlockedUntilProcessDeath() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Repo(catalog())
            repo.checkpoint = saved(LibrarySnapshotPlanner.accountKey(email))
            // Первое чтение падает: раньше это навсегда блокировало восстановление.
            repo.failReads = 1
            val connection = Connection()
            val model = PlayerViewModel(connection, repo,
                CredentialsProvider { StoredWebDavLogin(email, "stored-password") },
                NoArtwork, StandardTestDispatcher(testScheduler), { true })
            advanceUntilIdle()

            // Повтор запускает таймер задания, а не эмиссия снимка.
            assertEquals("timer-driven retry, not a snapshot-driven one", 2, repo.reads)
            assertEquals(1, connection.restores)
            assertEquals(listOf(id(1), id(2)), connection.lastRestored?.trackIds)
            assertEquals("PAUSED", model.state.value.playback.playbackState)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun restoreStopsRetryingAfterBudgetSoPersistentFailureDoesNotSpin() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Repo(catalog())
            repo.checkpoint = saved(LibrarySnapshotPlanner.accountKey(email))
            repo.failReads = Int.MAX_VALUE
            val connection = Connection()
            PlayerViewModel(connection, repo,
                CredentialsProvider { StoredWebDavLogin(email, "stored-password") },
                NoArtwork, StandardTestDispatcher(testScheduler), { true })
            advanceUntilIdle()
            // Бюджет работает без единой эмиссии: повтор идёт по таймеру задания, а не по снимкам.
            assertEquals("rounds must be exactly the budget", 3, repo.reads)
            repeat(10) {
                connection.emit(PlaybackSnapshot(connected = true, positionMs = it.toLong() + 1L))
                advanceUntilIdle()
            }
            assertEquals("snapshot ticks must not refill the budget", 3, repo.reads)
            assertEquals(0, connection.restores)
            assertEquals(QueueRestoreCode.RESTORE_BUDGET_EXHAUSTED,
                QueuePersistenceDiagnostics.state.value.result)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun accountChangeRefillsTheRestoreBudget() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Repo(catalog())
            repo.checkpoint = saved(LibrarySnapshotPlanner.accountKey(email))
            repo.failReads = Int.MAX_VALUE
            val connection = Connection()
            val model = PlayerViewModel(connection, repo,
                CredentialsProvider { StoredWebDavLogin(email, "stored-password") },
                NoArtwork, StandardTestDispatcher(testScheduler), { true })
            advanceUntilIdle()
            assertEquals(3, repo.reads)
            assertEquals(0, connection.restores)

            repo.failReads = 0
            repo.checkpoint = saved(LibrarySnapshotPlanner.accountKey(otherEmail))
            model.onCredentialsPersisted(otherEmail, "second-password")
            advanceUntilIdle()

            assertEquals("new account gets a full budget and one attempt", 4, repo.reads)
            assertEquals(1, connection.restores)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun repersistingSameCredentialsDoesNotRefillTheRestoreBudget() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Repo(catalog())
            repo.checkpoint = saved(LibrarySnapshotPlanner.accountKey(email))
            repo.failReads = Int.MAX_VALUE
            val connection = Connection()
            val model = PlayerViewModel(connection, repo,
                CredentialsProvider { StoredWebDavLogin(email, "stored-password") },
                NoArtwork, StandardTestDispatcher(testScheduler), { true })
            advanceUntilIdle()
            assertEquals(3, repo.reads)

            // Проверка WebDAV повторяется при каждом входе; та же учётная запись не возобновляет бюджет.
            model.onCredentialsPersisted(email, "stored-password")
            advanceUntilIdle()

            assertEquals("re-persisting the same login must not reset the counter", 3, repo.reads)
            assertEquals(0, connection.restores)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun budgetExhaustedRestoreHealsAfterServiceReconnect() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Repo(catalog())
            repo.checkpoint = saved(LibrarySnapshotPlanner.accountKey(email))
            repo.failReads = Int.MAX_VALUE
            val connection = Connection()
            PlayerViewModel(connection, repo,
                CredentialsProvider { StoredWebDavLogin(email, "stored-password") },
                NoArtwork, StandardTestDispatcher(testScheduler), { true })
            advanceUntilIdle()
            assertEquals(3, repo.reads)
            assertEquals(0, connection.restores)

            repo.failReads = 0
            repeat(3) {
                connection.emit(PlaybackSnapshot(connected = true, positionMs = 100L + it))
                advanceUntilIdle()
            }
            assertEquals(3, repo.reads)

            connection.emit(PlaybackSnapshot(connected = false))
            advanceUntilIdle()
            connection.emit(PlaybackSnapshot(connected = true, positionMs = 1L))
            advanceUntilIdle()

            assertEquals("healing after an external event", 4, repo.reads)
            assertEquals(1, connection.restores)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun signOutStopsRestoreReadsPastTheBackoffWindow() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = AccountSession()
            val repo = Repo(catalog())
            repo.checkpoint = saved(LibrarySnapshotPlanner.accountKey(email))
            repo.failReads = Int.MAX_VALUE
            val connection = Connection()
            PlayerViewModel(
                connection, repo,
                CredentialsProvider { StoredWebDavLogin(email, "stored-password") },
                NoArtwork, StandardTestDispatcher(testScheduler), { true },
                accountEvents = session.state,
            )
            testScheduler.runCurrent()
            assertEquals("round 1 must have run", 1, repo.reads)

            session.beginSignOut(LibrarySnapshotPlanner.accountKey(email))
            advanceUntilIdle()
            session.finishSignOut(LibrarySnapshotPlanner.accountKey(email))
            advanceUntilIdle()
            testScheduler.advanceTimeBy(60_000L)
            testScheduler.runCurrent()

            assertEquals("no reads after sign-out, even past the window", 1, repo.reads)
            assertEquals(0, connection.restores)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun legacy3647TrackCheckpointIsWindowedAndMarkedForImmediateRewrite() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val ids = (1..3_647).map(::id)
            val snapshot = LibrarySnapshot(
                ids.mapIndexed { index, trackId ->
                    OfflineTrackRow(id = trackId, title = "Track $index", artist = "Artist", album = "Album")
                },
                ids.associateWith { trackId ->
                    QueueTrack(trackId, "Track", "/AllMusic/$trackId.mp3", 100L, null)
                },
            )
            val source = LibrarySnapshotPlanner.accountKey(email)
            val repo = Repo(snapshot).apply {
                checkpoint = PersistedQueueState(
                    trackIds = ids,
                    selectedTrackId = ids[1_823],
                    positionMs = 42_321L,
                    repeatMode = Player.REPEAT_MODE_ALL,
                    shuffleEnabled = false,
                    shuffledIndices = null,
                    sourceKey = source,
                    savedAtEpochMs = 123L,
                    queueRevision = "01234567-89ab-cdef-0123-456789abcdef",
                )
            }
            val connection = Connection()
            PlayerViewModel(connection, repo,
                CredentialsProvider { StoredWebDavLogin(email, "stored-password") },
                NoArtwork, StandardTestDispatcher(testScheduler), { true })
            advanceUntilIdle()

            assertEquals(1, connection.restores)
            assertEquals(100, connection.lastQueue.size)
            assertEquals(ids[1_823], connection.lastRestored?.selectedTrackId)
            assertEquals(42_321L, connection.lastRestored?.positionMs)
            assertEquals(Player.REPEAT_MODE_ALL, connection.lastRestored?.repeatMode)
            assertTrue(connection.lastFiltered)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun verifiedAndPersistedLoginAllowsQueueRestore() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Repo(catalog())
            repo.checkpoint = saved(LibrarySnapshotPlanner.accountKey(email))
            val connection = Connection()
            val model = PlayerViewModel(connection, repo,
                CredentialsProvider { null }, NoArtwork,
                StandardTestDispatcher(testScheduler), { true })
            advanceUntilIdle()
            assertEquals(0, connection.restores)
            model.onCredentialsPersisted(email, "stored-password")
            advanceUntilIdle()
            assertEquals(1, connection.restores)
            assertEquals(0, connection.plays)
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

    @Test fun appScopeSignOutStopsRestoringAndDropsOldMiniPlayerState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = AccountSession()
            val repo = Repo(catalog())
            repo.checkpoint = saved(LibrarySnapshotPlanner.accountKey(email))
            val connection = Connection()
            val vm = PlayerViewModel(
                connection, repo,
                CredentialsProvider { StoredWebDavLogin(email, "stored-password") },
                NoArtwork, StandardTestDispatcher(testScheduler), { true },
                accountEvents = session.state,
            )
            advanceUntilIdle()
            assertEquals(1, connection.restores)
            vm.onNowPlaying(true)
            session.beginSignOut(LibrarySnapshotPlanner.accountKey(email))
            advanceUntilIdle()
            assertFalse(vm.state.value.nowPlaying)
            session.finishSignOut(LibrarySnapshotPlanner.accountKey(email))
            advanceUntilIdle()
            assertEquals(0, vm.state.value.playback.queueTotal)
            assertFalse(vm.state.value.nowPlaying)
            assertNull(vm.state.value.artwork)
            vm.onCredentialsPersisted(email, "stored-password")
            advanceUntilIdle()
            assertEquals(1, connection.restores) // blocked by account session
        } finally { Dispatchers.resetMain() }
    }

    @Test fun libraryRevisionReArmsTheBudgetAfterATerminalOutcome() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            // Медиатека пуста: checkpoint цел, но ни один трек не готов — исход терминальный.
            val repo = Repo(LibrarySnapshot(emptyList(), emptyMap()))
            repo.checkpoint = saved(LibrarySnapshotPlanner.accountKey(email))
            val connection = Connection()
            val revisions = MutableStateFlow(0L)
            PlayerViewModel(connection, repo,
                CredentialsProvider { StoredWebDavLogin(email, "stored-password") },
                NoArtwork, StandardTestDispatcher(testScheduler), { true },
                dataRevision = revisions)
            advanceUntilIdle()
            assertEquals("terminal outcome reads the checkpoint once", 1, repo.reads)
            assertEquals(0, connection.restores)

            // Импорт наполнил медиатеку: новая ревизия обязана вернуть бюджет,
            // иначе заявка удерживается до пересоздания ViewModel.
            repo.items = catalog()
            revisions.value = 1L
            advanceUntilIdle()

            assertEquals("library revision re-arms the claim", 2, repo.reads)
            assertEquals(1, connection.restores)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun rearmArrivingDuringARoundIsNotLostAfterATerminalOutcome() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            // Каталог пуст: раунд закончится терминально на устаревшем снимке.
            val repo = Repo(LibrarySnapshot(emptyList(), emptyMap()))
            repo.checkpoint = saved(LibrarySnapshotPlanner.accountKey(email))
            val connection = Connection()
            val revisions = MutableStateFlow(0L)
            PlayerViewModel(connection, repo,
                CredentialsProvider { StoredWebDavLogin(email, "stored-password") },
                NoArtwork, StandardTestDispatcher(testScheduler), { true },
                dataRevision = revisions)
            // Импорт завершается, пока раунд ещё внутри чтения checkpoint.
            repo.duringRead = {
                repo.items = catalog()
                revisions.value = 1L
            }
            advanceUntilIdle()

            // Событие не должно потеряться: оно обязано вернуть бюджет и восстановить очередь.
            assertEquals("re-arm must survive the in-flight round", 2, repo.reads)
            assertEquals(1, connection.restores)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun onlyRealMedia3DurationIsSavedOncePerTrack() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val sourceKey = LibrarySnapshotPlanner.accountKey(email)
            val repo = Repo(catalog())
            repo.checkpoint = saved(sourceKey).copy(durationMs = 77_000L)
            val connection = Connection()
            PlayerViewModel(
                connection, repo,
                CredentialsProvider { StoredWebDavLogin(email, "stored-password") },
                NoArtwork, StandardTestDispatcher(testScheduler), { true },
            )
            advanceUntilIdle()
            assertTrue(repo.durationSaves.isEmpty())

            connection.emit(PlaybackSnapshot(
                connected = true,
                currentTrackId = id(1),
                sourceKey = sourceKey,
                durationMs = 77_000L,
                knownDurationMs = 181_000L,
                positionMs = 1_000L,
                queueTotal = 2,
            ))
            advanceUntilIdle()
            connection.emit(connection.state.value.copy(positionMs = 2_000L))
            advanceUntilIdle()
            assertEquals(listOf(Triple(sourceKey, id(1), 181_000L)), repo.durationSaves)
        } finally { Dispatchers.resetMain() }
    }

}
