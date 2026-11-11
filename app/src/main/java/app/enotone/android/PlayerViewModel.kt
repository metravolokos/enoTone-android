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
internal class PlayerViewModel(
    private val connection: PlaybackConnection,
    private val repository: LibraryRepository,
    private val credentialsProvider: CredentialsProvider,
    private val artworkLoader: LibraryArtworkLoader,
    private val io: CoroutineDispatcher,
    private val useCache: () -> Boolean,
) : ViewModel() {
    private val mutable = MutableStateFlow(SharedPlayerUiState())
    val state: StateFlow<SharedPlayerUiState> = mutable
    private var login: StoredWebDavLogin? = null
    private var catalog: LibrarySnapshot? = null
    private var covers: AlbumArtworkCatalog? = null
    private var artworkJob: Job? = null
    private var restoredAccount: String? = null
    private var loadingAccount: String? = null

    init {
        viewModelScope.launch {
            connection.state.collect { current ->
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
            } else {
                login = stored
                ensureCatalog(stored)
                maybeRestore()
            }
        }
    }

    /** For regular RAM-only playback; persisted login is always via CredentialsProvider. */
    fun onCredentialsChanged(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) return
        if (login?.email == email && login?.password == password) return
        login = StoredWebDavLogin(email, password)
        catalog = null
        covers = null
        loadingAccount = null
        loadArtwork()
    }

    private fun ensureCatalog(saved: StoredWebDavLogin) {
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
                if (login?.email == saved.email) {
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

    private fun maybeRestore() {
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
        if (covers == null) ensureCatalog(saved)
        val item = mutable.value.playback.currentTrackId
        val group = covers?.byTrackId?.get(item)
        val album = catalog?.tracks?.firstOrNull { it.id == item }?.album
        mutable.update { it.copy(artwork = null, largeArtwork = null, album = album) }
        if (group == null) return
        artworkJob = viewModelScope.launch {
            artworkLoader.peek(saved.email, group, 512)?.let { picture ->
                mutable.update { it.copy(artwork = picture) }
            }
            artworkLoader.cached(saved.email, group, 512)?.let { picture ->
                mutable.update { it.copy(artwork = picture) }
            }
            // Respect the existing no-GET-until-Play rule for restored queues.
            val current = connection.state.value.playbackState
            if (current == "PLAYING" || current == "PAUSED") {
                artworkLoader.fetch(saved.email, saved.password, group, 512)?.let { picture ->
                    mutable.update { it.copy(artwork = picture) }
                }
            }
            if (mutable.value.nowPlaying) {
                artworkLoader.cached(saved.email, group, 1200)?.let { picture ->
                    mutable.update { it.copy(largeArtwork = picture) }
                }
                if (current == "PLAYING" || current == "PAUSED") {
                    artworkLoader.fetch(saved.email, saved.password, group, 1200)?.let { picture ->
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
                        useCache = { PlaybackDiagnosticsRuntime.useCacheOnNextPlay },
                    ) as T
                }
            }
    }
}
