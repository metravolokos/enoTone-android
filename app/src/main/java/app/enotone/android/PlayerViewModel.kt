package app.enotone.android

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.enotone.library.LibraryRepository
import app.enotone.library.LibrarySnapshot
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.playback.QueueEditSource
import app.enotone.playback.QueuePersistenceDiagnostics
import app.enotone.playback.QueueRestoreCode
import app.enotone.playback.QueueStateRestorer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Match the approved 512px mini artwork and 1200px full cover masters.
private const val PLAYER_COMPACT_COVER_PX = 512
private const val PLAYER_FULL_COVER_PX = 1200

/** Terminal outcomes hold the claim; a transient one spends one budget round. */
private enum class RestoreOutcome { RESTORED, TERMINAL, TRANSIENT }

/** The credential storage policy is replaceable in M6-0/4b without changing player code. */
internal fun interface CredentialsProvider {
    suspend fun credentials(): StoredWebDavLogin?
}

internal class VaultCredentialsProvider(private val context: Context) : CredentialsProvider {
    override suspend fun credentials(): StoredWebDavLogin? =
        if (SignOutMarker.pending(context) || FirstRunPreferences(context).reauthenticationRequired()) null
        else runCatching { LocalCredentialVault.restore(context) }.getOrNull()
}

internal data class SharedPlayerUiState(
    val playback: PlaybackSnapshot = PlaybackSnapshot(),
    val artwork: Bitmap? = null,
    val largeArtwork: Bitmap? = null,
    val album: String? = null,
    val nowPlaying: Boolean = false,
)

/**
 * Activity-scoped MediaSession observer. Unlike LibraryViewModel it survives
 * Home/Library/Settings transitions and attempts restore even on Home.
 */
// All transport commands stay together in one Activity-scoped owner; manual DI keeps test injection.
@Suppress("TooManyFunctions", "LongParameterList")
internal class PlayerViewModel(
    private val connection: PlaybackConnection,
    private val repository: LibraryRepository,
    private val credentialsProvider: CredentialsProvider,
    private val artworkLoader: LibraryArtworkLoader,
    private val io: CoroutineDispatcher,
    private val useCache: () -> Boolean,
    private val accountEvents: StateFlow<AccountState> = MutableStateFlow(AccountState()),
    private val dataRevision: StateFlow<Long> = LibraryDataRevision.value,
) : ViewModel() {
    private val mutable = MutableStateFlow(SharedPlayerUiState())
    val state: StateFlow<SharedPlayerUiState> = mutable
    private var login: StoredWebDavLogin? = null
    private var catalog: LibrarySnapshot? = null
    private var covers: AlbumArtworkCatalog? = null
    private var artworkJob: Job? = null
    private var restoreJob: Job? = null
    private var restoredAccount: String? = null

    /**
     * Transient rounds already spent by the claim in [restoredAccount]; the claim is the
     * budget's lifetime, so the counter is refilled only when the claim is released
     * ([releaseRestoreClaim], account change, sign-out, service reconnect).
     */
    private var restoreAttempts = 0

    /**
     * Set when a re-arm event (reconnect, library revision) arrives while a round is still
     * running. Without it the event would be dropped: the round ends with the claim held and
     * no further trigger would ever retry.
     */
    private var restoreRearmPending = false
    private var hostResumed = false

    // Ключ включает длительность: если файл заменили (импорт сбросил durationMs в null) и у новой
    // версии другая длительность, запись повторится без пересоздания ViewModel (ревью #125).
    private val durationSaveAttempts = mutableSetOf<Triple<String, String, Long>>()

    // Verified and encrypted logins are the only restoration credentials.
    private var restoreAllowed = false
    private var loadingAccount: String? = null
    private var catalogGeneration = 0

    init {
        viewModelScope.launch {
            accountEvents.collect { event ->
                val currentLogin = login
                val affected = currentLogin != null &&
                    event.sourceKey == LibrarySnapshotPlanner.accountKey(currentLogin.email)
                when {
                    affected && event.phase == AccountPhase.SIGNING_OUT -> onSignOutStarted()
                    event.phase == AccountPhase.SIGNED_OUT && (affected ||
                        currentLogin == null) -> onSignedOut()
                    affected && event.phase == AccountPhase.SIGN_OUT_FAILED -> onSignOutFailed()
                }
            }
        }
        viewModelScope.launch {
            connection.state.collect { current ->
                if (accountEvents.value.phase == AccountPhase.SIGNED_OUT) {
                    mutable.update { it.copy(playback = PlaybackSnapshot()) }
                    return@collect
                }
                val old = mutable.value.playback
                mutable.update { it.copy(playback = current) }
                updateNowPlayingBufferVisibility()
                saveKnownDuration(current)
                if (current.currentTrackId != old.currentTrackId ||
                    (current.queueTotal > 0 && old.queueTotal == 0)) {
                    loadArtwork()
                }
                if (current.connected && !old.connected) rearmRestoreBudget()
                if (current.connected && current.queueTotal == 0) maybeRestore()
            }
        }
        viewModelScope.launch {
            // Аудит п. 2: каталог плеера (альбом, обложки) устаревает после импорта и индексации.
            dataRevision.drop(1).collect {
                catalogGeneration++
                catalog = null
                covers = null
                loadingAccount = null
                // A terminal restore outcome usually means the catalog could not supply the
                // checkpoint tracks; a new library revision is exactly the event that can fix
                // that, so it re-arms the budget instead of leaving the claim held.
                rearmRestoreBudget()
                login?.takeIf { restoreAllowed }?.let(::ensureCatalog)
            }
        }
        connection.connect()
        viewModelScope.launch {
            val stored = withContext(io) { credentialsProvider.credentials() }
            if (stored == null) {
                QueuePersistenceDiagnostics.result(QueueRestoreCode.NO_CREDENTIALS)
            } else if (!accountEvents.value.blocks(LibrarySnapshotPlanner.accountKey(stored.email))) {
                login = stored
                restoreAllowed = true
                saveKnownDuration(connection.state.value)
                ensureCatalog(stored)
                maybeRestore()
            }
        }
    }

    /** Called only after WebDAV verification AND a successful vault write. */
    fun onCredentialsPersisted(
        email: String,
        password: String,
        endpoint: SourceEndpoint = SourceEndpoint.MAILRU_ALL_MUSIC,
    ) {
        if (email.isBlank() || password.isBlank() ||
            accountEvents.value.blocks(LibrarySnapshotPlanner.accountKey(email))) return
        val verified = StoredWebDavLogin(email, password, endpoint)
        if (login != verified) {
            restoreJob?.cancel()
            catalogGeneration++
            login = verified
            catalog = null
            covers = null
            loadingAccount = null
            releaseRestoreClaim()
        }
        restoreAllowed = true
        saveKnownDuration(connection.state.value)
        ensureCatalog(verified)
        maybeRestore()
        loadArtwork()
    }

    /** Blocks the bind/queue-restoration race while ordered cleanup runs. */
    fun onSignOutStarted() {
        restoreAllowed = false
        artworkJob?.cancel()
        restoreJob?.cancel()
        mutable.update { it.copy(nowPlaying = false, artwork = null, largeArtwork = null) }
        connection.setNowPlayingVisible(false)
    }

    fun onSignOutFailed() {
        // Cleanup may have already deleted Room records. Keep credentials
        // for retry, but do not rehydrate a partially erased queue.
        restoreAllowed = false
        // Restore writes the queue into the service; an in-flight round must not
        // repopulate it after a failed cleanup.
        restoreJob?.cancel()
    }

    fun onSignedOut() {
        catalogGeneration++
        restoreAllowed = false
        login = null
        catalog = null
        covers = null
        releaseRestoreClaim()
        loadingAccount = null
        artworkJob?.cancel()
        restoreJob?.cancel()
        mutable.value = SharedPlayerUiState()
        connection.setNowPlayingVisible(false)
    }

    private fun ensureCatalog(saved: StoredWebDavLogin) {
        if (accountEvents.value.blocks(LibrarySnapshotPlanner.accountKey(saved.email))) return
        if (catalog != null || loadingAccount == saved.email) return
        loadingAccount = saved.email
        val generation = catalogGeneration
        viewModelScope.launch {
            try {
                val loaded = repository.load(saved.email)
                val artwork = withContext(io) {
                    AlbumArtworkCatalog.build(loaded.tracks) { id ->
                        loaded.preparedById[id]?.let {
                            PlayingArtworkTarget(it.id, it.encodedPath, it.remoteSize, it.remoteEtag)
                        }
                    }
                }
                val current = generation == catalogGeneration && login?.email == saved.email
                if (current && restoreAllowed &&
                    !accountEvents.value.blocks(LibrarySnapshotPlanner.accountKey(saved.email))) {
                    catalog = loaded
                    covers = artwork
                    loadArtwork()
                    maybeRestore()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                QueuePersistenceDiagnostics.result(QueueRestoreCode.NO_TRACKS_IN_ROOM)
            } finally {
                if (generation == catalogGeneration) loadingAccount = null
            }
        }
    }

    /** Bounded pause before the next round; one-based [consumedRounds]. */
    private fun restoreBackoffMs(consumedRounds: Int): Long =
        if (consumedRounds == 1) RESTORE_BACKOFF_FIRST_MS else RESTORE_BACKOFF_SECOND_MS

    /** Drops the claim and refills the budget; the next claim starts at round 1. */
    private fun releaseRestoreClaim() {
        restoredAccount = null
        restoreAttempts = 0
        restoreRearmPending = false
    }

    /**
     * Re-arms the budget on an external event that can plausibly fix a failed restore
     * (service reconnect, new library revision). A round in flight would only swallow the
     * event, so it is recorded and replayed by [restartAfterRejoin] once that round ends.
     */
    private fun rearmRestoreBudget() {
        if (restoreJob?.isActive == true) {
            restoreRearmPending = true
            return
        }
        releaseRestoreClaim()
    }

    /**
     * Restarts a restore that was re-armed while a round was still running. Runs from the
     * job's completion handler, so [restoreJob] is already finished and [maybeRestore] can
     * claim again instead of being rejected by its own in-flight guard.
     */
    private fun restartAfterRejoin() {
        if (!restoreRearmPending) return
        restoreRearmPending = false
        releaseRestoreClaim()
        maybeRestore()
    }

    @Suppress("ReturnCount")
    private suspend fun runRestoreRounds(saved: StoredWebDavLogin, sourceKey: String) {
        while (restoreAttempts < MAX_RESTORE_ATTEMPTS) {
            // Каталог разрешается в момент выстрела, а не в момент планирования.
            val cached = catalog ?: run {
                releaseRestoreClaim()
                ensureCatalog(saved)
                return
            }
            restoreAttempts += 1
            val outcome = try {
                attemptRestore(saved, cached, sourceKey)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                QueuePersistenceDiagnostics.result(QueueRestoreCode.POSITION_INVALID)
                RestoreOutcome.TRANSIENT
            }
            if (applyRestoreOutcome(outcome) || outcome != RestoreOutcome.TRANSIENT) return
            delay(restoreBackoffMs(restoreAttempts))
        }
    }

    /**
     * Handles one round's outcome; returns true when the round loop must stop. A repair event
     * that arrived while this round ran is left in [restoreRearmPending] and honoured by the
     * job's completion handler, which is the only place able to claim again.
     */
    private fun applyRestoreOutcome(outcome: RestoreOutcome): Boolean {
        val stop = when (outcome) {
            RestoreOutcome.RESTORED -> true
            RestoreOutcome.TERMINAL -> true
            RestoreOutcome.TRANSIENT -> restoreAttempts >= MAX_RESTORE_ATTEMPTS
        }
        when (outcome) {
            RestoreOutcome.RESTORED -> {
                // Nothing left to repair: a pending re-arm must not leak into the next claim.
                restoreRearmPending = false
                QueuePersistenceDiagnostics.result(QueueRestoreCode.RESTORED)
                loadArtwork()
            }
            RestoreOutcome.TERMINAL -> Unit
            RestoreOutcome.TRANSIENT -> if (stop) {
                QueuePersistenceDiagnostics.result(QueueRestoreCode.RESTORE_BUDGET_EXHAUSTED)
            }
        }
        return stop
    }

    // Guarded once per source to prevent repeated bind/restore races on Home.
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun maybeRestore() {
        if (!restoreAllowed || login?.let {
                accountEvents.value.blocks(LibrarySnapshotPlanner.accountKey(it.email))
            } == true) return
        // Один владелец повторов: пока раунд жив, второй не стартует.
        if (restoreJob?.isActive == true) return
        val snapshot = mutable.value.playback
        if (!snapshot.connected) return
        if (snapshot.queueTotal > 0) return
        val saved = login ?: return
        if (catalog == null) {
            ensureCatalog(saved)
            return
        }
        val sourceKey = LibrarySnapshotPlanner.accountKey(saved.email)
        if (restoredAccount == sourceKey) return
        restoredAccount = sourceKey
        val job = viewModelScope.launch { runRestoreRounds(saved, sourceKey) }
        restoreJob = job
        // A re-arm that arrived mid-round is honoured here, after the guard above has been
        // released by completion; doing it inside the round would be rejected as in-flight.
        job.invokeOnCompletion { if (restoreRearmPending) restartAfterRejoin() }
    }

    @Suppress("ReturnCount")
    private suspend fun attemptRestore(
        saved: StoredWebDavLogin,
        cached: LibrarySnapshot,
        sourceKey: String,
    ): RestoreOutcome {
        val checkpoint = repository.readSavedQueue() ?: return RestoreOutcome.TERMINAL
        if (!QueueEditSource.belongsTo(checkpoint.sourceKey, sourceKey)) {
            QueuePersistenceDiagnostics.result(QueueRestoreCode.SOURCE_MISMATCH)
            return RestoreOutcome.TERMINAL
        }
        val filtered = withContext(io) {
            QueueStateRestorer.filter(checkpoint, cached.preparedById.keys)
        } ?: return RestoreOutcome.TERMINAL
        val queue = filtered.trackIds.mapNotNull(cached.preparedById::get)
        // An in-flight restore may have started before the user confirmed sign-out.
        // Never repopulate the queue during cleanup.
        if (!restoreAllowed || login != saved || accountEvents.value.blocks(sourceKey)) {
            return RestoreOutcome.TERMINAL
        }
        if (queue.size != filtered.trackIds.size) {
            QueuePersistenceDiagnostics.result(QueueRestoreCode.NO_TRACKS_IN_ROOM)
            return RestoreOutcome.TERMINAL
        }
        if (connection.state.value.queueTotal > 0) {
            QueuePersistenceDiagnostics.result(QueueRestoreCode.SERVICE_HAS_QUEUE)
            return RestoreOutcome.TERMINAL
        }
        val success = connection.restoreQueue(saved.email, saved.password, queue,
            filtered, useCache(), queue.size != checkpoint.trackIds.size)
        if (!success) QueuePersistenceDiagnostics.result(QueueRestoreCode.SERVICE_HAS_QUEUE)
        return if (success) RestoreOutcome.RESTORED else RestoreOutcome.TRANSIENT
    }

    private fun loadArtwork() {
        artworkJob?.cancel()
        val saved = login ?: return
        if (accountEvents.value.blocks(LibrarySnapshotPlanner.accountKey(saved.email))) return
        if (covers == null) ensureCatalog(saved)
        val item = mutable.value.playback.currentTrackId
        val group = covers?.byTrackId?.get(item)
        val album = catalog?.tracks?.firstOrNull { it.id == item }?.album
        mutable.update { it.copy(artwork = null, largeArtwork = null, album = album) }
        if (group == null) return
        artworkJob = viewModelScope.launch {
            artworkLoader.peek(saved.email, group, PLAYER_COMPACT_COVER_PX)?.let { picture ->
                mutable.update { it.copy(artwork = picture) }
            }
            artworkLoader.cached(saved.email, group, PLAYER_COMPACT_COVER_PX)?.let { picture ->
                mutable.update { it.copy(artwork = picture) }
            }
            // Respect the existing no-GET-until-Play rule for restored queues.
            val current = connection.state.value.playbackState
            if (current == "PLAYING" || current == "PAUSED") {
                artworkLoader.fetch(saved.email, saved.password, group,
                    PLAYER_COMPACT_COVER_PX, saved.endpoint)?.let { picture ->
                    mutable.update { it.copy(artwork = picture) }
                }
            }
            if (mutable.value.nowPlaying) {
                artworkLoader.cached(saved.email, group, PLAYER_FULL_COVER_PX)?.let { picture ->
                    mutable.update { it.copy(largeArtwork = picture) }
                }
                if (current == "PLAYING" || current == "PAUSED") {
                    artworkLoader.fetch(saved.email, saved.password, group,
                        PLAYER_FULL_COVER_PX, saved.endpoint)?.let { picture ->
                        mutable.update { it.copy(largeArtwork = picture) }
                    }
                }
            }
        }
    }

    /** One guarded Room update per source/track after Media3 has real duration data. */
    private fun saveKnownDuration(snapshot: PlaybackSnapshot) {
        val sourceKey = login?.let { LibrarySnapshotPlanner.accountKey(it.email) }
        val trackId = snapshot.currentTrackId
        val durationMs = snapshot.knownDurationMs?.takeIf { it > 0L }
        val saveRequest = sourceKey?.let { key ->
            trackId?.let { id -> durationMs?.let { value -> Triple(key, id, value) } }
        }
        if (restoreAllowed && saveRequest != null) {
            val (key, id, value) = saveRequest
            if (isDurationSourceAllowed(snapshot, key) &&
                durationSaveAttempts.add(Triple(key, id, value))) {
                viewModelScope.launch {
                    try {
                        withContext(io) {
                            repository.saveDurationIfUnknown(key, id, value)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // A transient Room/IO failure must not cost the duration forever:
                        // forget the attempt so a later snapshot can retry it. Polling still
                        // does not hammer the write, because success is the common path.
                        durationSaveAttempts.remove(Triple(key, id, value))
                    }
                }
            }
        }
    }

    private fun isDurationSourceAllowed(snapshot: PlaybackSnapshot, sourceKey: String): Boolean =
        snapshot.sourceKey?.let { QueueEditSource.belongsTo(it, sourceKey) } == true &&
            !accountEvents.value.blocks(sourceKey)

    fun onNowPlaying(open: Boolean) {
        mutable.update { it.copy(nowPlaying = open) }
        updateNowPlayingBufferVisibility()
        if (open) loadArtwork()
    }

    /** Buffer sampling is active only while both the host and Now Playing are visible. */
    fun onHostResumed(resumed: Boolean) {
        hostResumed = resumed
        updateNowPlayingBufferVisibility()
    }

    private fun updateNowPlayingBufferVisibility() {
        val ui = mutable.value
        connection.setNowPlayingVisible(
            hostResumed && ui.nowPlaying && ui.playback.queueTotal > 0,
        )
    }
    fun onPlayPause() = connection.playPause()
    fun onRetryCurrent(): Boolean = connection.retryCurrent()
    fun onNext() = connection.playNext()
    fun onPrevious() = connection.playPrevious()
    fun onQueuePositionSelected(position: Int) = connection.skipToQueuePosition(position)
    fun onShuffle() = connection.toggleShuffle()
    fun onRepeat() = connection.cycleRepeatMode()
    fun onSeek(fraction: Float) = connection.seekToFraction(fraction)

    override fun onCleared() {
        connection.disconnect() // MediaSessionService keeps playback alive.
        super.onCleared()
    }

    companion object {
        /** Transient restore rounds per claim; the last one reports RESTORE_BUDGET_EXHAUSTED. */
        private const val MAX_RESTORE_ATTEMPTS = 3

        /** Bounded pause after round 1 and round 2; the pair caps the retry window at 5 s. */
        private const val RESTORE_BACKOFF_FIRST_MS = 1_000L

        private const val RESTORE_BACKOFF_SECOND_MS = 4_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == PlayerViewModel::class.java)
                    return PlayerViewModel(
                        connection = container.playbackConnection,
                        repository = container.libraryRepository,
                        credentialsProvider = container.credentialsProvider,
                        artworkLoader = RepositoryArtworkLoader(container.artworkPreviewRepository),
                        io = container.io,
                        useCache = { true },
                        accountEvents = container.accountSession.state,
                    ) as T
                }
            }
    }
}
