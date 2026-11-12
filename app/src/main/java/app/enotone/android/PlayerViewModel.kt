package app.enotone.android

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.enotone.library.LibraryRepository
import app.enotone.library.LibrarySnapshot
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.playback.QueuePersistenceDiagnostics
import app.enotone.playback.QueueRestoreCode
import app.enotone.playback.QueueStateRestorer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Match the approved 512px mini artwork and 1200px full cover masters.
private const val PLAYER_COMPACT_COVER_PX = 512
private const val PLAYER_FULL_COVER_PX = 1200

/** The credential storage policy is replaceable in M6-0/4b without changing player code. */
internal fun interface CredentialsProvider {
    suspend fun credentials(): StoredWebDavLogin?
}

internal class VaultCredentialsProvider(private val context: Context) : CredentialsProvider {
    override suspend fun credentials(): StoredWebDavLogin? =
        runCatching { LocalCredentialVault.restore(context) }.getOrNull()
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
// All transport commands stay together in one Activity-scoped owner.
@Suppress("TooManyFunctions")
internal class PlayerViewModel(
    private val connection: PlaybackConnection,
    private val repository: LibraryRepository,
    private val credentialsProvider: CredentialsProvider,
    private val artworkLoader: LibraryArtworkLoader,
    private val io: CoroutineDispatcher,
    private val useCache: () -> Boolean,
    private val accountEvents: StateFlow<AccountState> = MutableStateFlow(AccountState()),
) : ViewModel() {
    private val mutable = MutableStateFlow(SharedPlayerUiState())
    val state: StateFlow<SharedPlayerUiState> = mutable
    private var login: StoredWebDavLogin? = null
    private var catalog: LibrarySnapshot? = null
    private var covers: AlbumArtworkCatalog? = null
    private var artworkJob: Job? = null
    private var restoredAccount: String? = null

    // Verified and encrypted logins are the only restoration credentials.
    private var restoreAllowed = false
    private var loadingAccount: String? = null

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
                if (current.currentTrackId != old.currentTrackId ||
                    (current.queueTotal > 0 && old.queueTotal == 0)) {
                    loadArtwork()
                }
                if (current.connected && current.queueTotal == 0) maybeRestore()
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
                ensureCatalog(stored)
                maybeRestore()
            }
        }
    }

    /** Called only after WebDAV verification AND a successful vault write. */
    fun onCredentialsPersisted(email: String, password: String) {
        if (email.isBlank() || password.isBlank() ||
            accountEvents.value.blocks(LibrarySnapshotPlanner.accountKey(email))) return
        val verified = StoredWebDavLogin(email, password)
        if (login != verified) {
            login = verified
            catalog = null
            covers = null
            loadingAccount = null
            restoredAccount = null
        }
        restoreAllowed = true
        ensureCatalog(verified)
        maybeRestore()
        loadArtwork()
    }

    /** Blocks the bind/queue-restoration race while ordered cleanup runs. */
    fun onSignOutStarted() {
        restoreAllowed = false
        artworkJob?.cancel()
        mutable.update { it.copy(nowPlaying = false, artwork = null, largeArtwork = null) }
    }

    fun onSignOutFailed() {
        // Cleanup may have already deleted Room records. Keep credentials
        // for retry, but do not rehydrate a partially erased queue.
        restoreAllowed = false
    }

    fun onSignedOut() {
        restoreAllowed = false
        login = null
        catalog = null
        covers = null
        restoredAccount = null
        loadingAccount = null
        artworkJob?.cancel()
        mutable.value = SharedPlayerUiState()
    }

    private fun ensureCatalog(saved: StoredWebDavLogin) {
        if (accountEvents.value.blocks(LibrarySnapshotPlanner.accountKey(saved.email))) return
        if (catalog != null || loadingAccount == saved.email) return
        loadingAccount = saved.email
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
                if (login?.email == saved.email && restoreAllowed &&
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
                loadingAccount = null
            }
        }
    }

    // Guarded once per source to prevent repeated bind/restore races on Home.
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun maybeRestore() {
        if (!restoreAllowed || login?.let {
                accountEvents.value.blocks(LibrarySnapshotPlanner.accountKey(it.email))
            } == true) return
        val snapshot = mutable.value.playback
        if (!snapshot.connected) return
        if (snapshot.queueTotal > 0) return
        val saved = login ?: return
        val cached = catalog
        if (cached == null) {
            ensureCatalog(saved)
            return
        }
        val sourceKey = LibrarySnapshotPlanner.accountKey(saved.email)
        if (restoredAccount == sourceKey) return
        restoredAccount = sourceKey
        viewModelScope.launch {
            try {
                val checkpoint = repository.readSavedQueue() ?: return@launch
                if (checkpoint.sourceKey != sourceKey) {
                    QueuePersistenceDiagnostics.result(QueueRestoreCode.SOURCE_MISMATCH)
                    return@launch
                }
                val filtered = withContext(io) {
                    QueueStateRestorer.filter(checkpoint, cached.preparedById.keys)
                }
                if (filtered == null) {
                    QueuePersistenceDiagnostics.result(QueueRestoreCode.NO_TRACKS_IN_ROOM)
                    return@launch
                }
                val queue = filtered.trackIds.mapNotNull(cached.preparedById::get)
                if (queue.size != filtered.trackIds.size) {
                    QueuePersistenceDiagnostics.result(QueueRestoreCode.NO_TRACKS_IN_ROOM)
                    return@launch
                }
                // An in-flight restore may have started before the user
                // confirmed sign-out. Never repopulate the queue during cleanup.
                if (!restoreAllowed || login != saved ||
                    accountEvents.value.blocks(sourceKey)) return@launch
                if (connection.state.value.queueTotal > 0) {
                    QueuePersistenceDiagnostics.result(QueueRestoreCode.SERVICE_HAS_QUEUE)
                    return@launch
                }
                val success = connection.restoreQueue(saved.email, saved.password, queue,
                    filtered, useCache(), queue.size != checkpoint.trackIds.size)
                if (success) {
                    QueuePersistenceDiagnostics.result(QueueRestoreCode.RESTORED)
                    loadArtwork()
                } else {
                    QueuePersistenceDiagnostics.result(QueueRestoreCode.SERVICE_HAS_QUEUE)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                QueuePersistenceDiagnostics.result(QueueRestoreCode.POSITION_INVALID)
            }
        }
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
                artworkLoader.fetch(saved.email, saved.password, group, PLAYER_COMPACT_COVER_PX)?.let { picture ->
                    mutable.update { it.copy(artwork = picture) }
                }
            }
            if (mutable.value.nowPlaying) {
                artworkLoader.cached(saved.email, group, PLAYER_FULL_COVER_PX)?.let { picture ->
                    mutable.update { it.copy(largeArtwork = picture) }
                }
                if (current == "PLAYING" || current == "PAUSED") {
                    artworkLoader.fetch(saved.email, saved.password, group, PLAYER_FULL_COVER_PX)?.let { picture ->
                        mutable.update { it.copy(largeArtwork = picture) }
                    }
                }
            }
        }
    }

    fun onNowPlaying(open: Boolean) {
        mutable.update { it.copy(nowPlaying = open) }
        if (open) loadArtwork()
    }
    fun onPlayPause() = connection.playPause()
    fun onNext() = connection.playNext()
    fun onPrevious() = connection.playPrevious()
    fun onShuffle() = connection.toggleShuffle()
    fun onRepeat() = connection.cycleRepeatMode()
    fun onSeek(fraction: Float) = connection.seekToFraction(fraction)

    override fun onCleared() {
        connection.disconnect() // MediaSessionService keeps playback alive.
        super.onCleared()
    }

    companion object {
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
