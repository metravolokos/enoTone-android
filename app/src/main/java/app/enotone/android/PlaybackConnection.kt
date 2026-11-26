package app.enotone.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.media3.common.C
import androidx.media3.common.Player
import app.enotone.playback.CachePhase
import app.enotone.playback.EnoTonePlaybackService
import app.enotone.playback.HandoffSnapshot
import app.enotone.playback.NextBufferState
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.PlaybackDataIssue
import app.enotone.playback.PlaybackDataIssueClassifier
import app.enotone.playback.PlaybackQueueSnapshot
import app.enotone.playback.PlaybackSetupTimings
import app.enotone.playback.QueueEditResult
import app.enotone.playback.QueueEditStatus
import app.enotone.playback.QueueTrack
import app.enotone.playback.TransientCacheStatus
import app.enotone.playback.resolveBufferedPositionMs
import app.enotone.playback.resolveNextBufferState
import app.enotone.playback.resolveVisiblePlayerBufferPositionMs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

// 500 ms preserves the existing position/seek polling cadence without a CPU spin.
private const val POSITION_POLL_MS = 500L

    /** A bound service that has not connected within this window is reported as an error. */
    private const val CONNECT_TIMEOUT_MS = 3_000L

/** Reconnect pauses, growing per failed attempt and capped, so a dead service backs off. */
private val CONNECT_BACKOFF_STEPS_MS = longArrayOf(0L, 1_000L, 4_000L, 10_000L, 30_000L)

/**
 * How long to wait before the next reconnect attempt. Attempts past the table reuse the last
 * value, so the retry keeps happening at a bounded rate instead of stopping for good.
 */
internal fun connectBackoffMs(attempts: Int): Long =
    CONNECT_BACKOFF_STEPS_MS[attempts.coerceIn(0, CONNECT_BACKOFF_STEPS_MS.lastIndex)]

/** No service/binder reference escapes to the ViewModel or the composable. */
internal data class PlaybackSnapshot(
    val connected: Boolean = false,
    val playbackState: String = "IDLE",
    val currentTrackId: String? = null,
    val sourceKey: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val positionMs: Long = 0,
    val bufferedPositionMs: Long = positionMs,
    val durationMs: Long = 0,
    /** Media3 duration, null until the current media item has a known duration. */
    val knownDurationMs: Long? = null,
    val canSeek: Boolean = false,
    val queuePosition: Int = 0,
    val queueTotal: Int = 0,
    val loadedQueueSources: Int = 0,
    val queueOrder: PlaybackQueueSnapshot? = null,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val errorCode: Int = 0,
    val errorReadIssue: PlaybackDataIssue = PlaybackDataIssue.UNKNOWN,
    val setupTimings: PlaybackSetupTimings? = null,
    val handoffInfo: HandoffSnapshot? = null,
    val cacheInfo: TransientCacheStatus = TransientCacheStatus(CachePhase.IDLE, 0L, 0L),
    val nextCacheInfo: TransientCacheStatus = TransientCacheStatus(CachePhase.IDLE, 0L, 0L),
    val nextBufferState: NextBufferState = NextBufferState.NONE,
)

/** Service connection is active only for the owning ViewModel's lifetime. */
// Queue actions carry credentials, source and selection as one explicit call.
@Suppress("LongParameterList")
internal interface PlaybackConnection {
    val state: StateFlow<PlaybackSnapshot>
    fun connect()
    fun disconnect()
    fun setNowPlayingVisible(visible: Boolean) = Unit
    fun playQueue(email: String, password: String, queue: List<QueueTrack>,
                  selectedId: String, useCache: Boolean, sourceKey: String)
    fun restoreQueue(email: String, password: String, queue: List<QueueTrack>,
                     saved: PersistedQueueState, useCache: Boolean, filtered: Boolean): Boolean
    /** Returns false when the local MediaSession binder is unavailable. */
    fun stopPlayback(): Boolean

    /**
     * Stops playback and erases the account's audio data, completing only when deletion
     * actually finished. Sign-out must await this before clearing the Keystore
     * (ADR-0080). Default returns an already-failed future so a test double that only
     * implements [stopPlayback] cannot silently look like a completed erase.
     */
    fun stopPlaybackAndEraseData(): CompletableFuture<Unit> =
        CompletableFuture<Unit>().apply {
            completeExceptionally(UnsupportedOperationException("erase not supported"))
        }
    fun playPause()

    /**
     * Retry only the current errored item, or reconnect when the service is gone — the sheet
     * that owns this action closes on true, and a dead service must not leave it stuck.
     * Returns false only when there is no error to retry while the service is alive.
     */
    fun retryCurrent(): Boolean = false
    fun playNext()
    fun playPrevious()
    fun skipToQueuePosition(position: Int) = Unit
    fun insertNext(tracks: List<QueueTrack>): QueueEditResult =
        QueueEditResult(QueueEditStatus.UNAVAILABLE)
    fun append(tracks: List<QueueTrack>): QueueEditResult =
        QueueEditResult(QueueEditStatus.UNAVAILABLE)
    fun cycleRepeatMode()
    fun toggleShuffle()
    fun seekToFraction(fraction: Float)
}

private fun playbackLabel(player: Player): String {
    if (player.playerError != null) return "ERROR"
    return when (player.playbackState) {
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> if (player.isPlaying) "PLAYING" else "PAUSED"
        Player.STATE_ENDED -> "ENDED"
        else -> "IDLE"
    }
}

/** Pure assembly of the Media3 reads and the UI-ready buffering values. */
internal data class PlaybackBufferingSnapshot(
    val positionMs: Long,
    val bufferedPositionMs: Long,
    val durationMs: Long,
    val knownDurationMs: Long?,
    val repeatMode: Int,
    val hasNext: Boolean,
    val nextCacheInfo: TransientCacheStatus,
    val nextBufferState: NextBufferState,
)

@Suppress("LongParameterList")
internal fun resolvePlaybackBufferingSnapshot(
    positionMs: Long,
    durationMs: Long,
    knownDurationMs: Long?,
    nowPlayingVisible: Boolean,
    readPlayerBufferedPositionMs: () -> Long,
    currentTrackFullyCached: Boolean,
    repeatMode: Int,
    nextMediaItemIndex: Int,
    nextCacheInfo: TransientCacheStatus,
    queueFullyCollected: Boolean,
): PlaybackBufferingSnapshot {
    val playerBuffer = resolveVisiblePlayerBufferPositionMs(
        nowPlayingVisible = nowPlayingVisible,
        positionMs = positionMs,
        readPlayerBufferedPositionMs = readPlayerBufferedPositionMs,
    )
    return PlaybackBufferingSnapshot(
        positionMs = positionMs,
        bufferedPositionMs = resolveBufferedPositionMs(
            positionMs = positionMs,
            durationMs = knownDurationMs ?: durationMs,
            playerBufferedPositionMs = playerBuffer,
            currentTrackFullyCached = currentTrackFullyCached,
        ),
        durationMs = durationMs,
        knownDurationMs = knownDurationMs,
        repeatMode = repeatMode,
        hasNext = nextMediaItemIndex != C.INDEX_UNSET,
        nextCacheInfo = nextCacheInfo,
        nextBufferState = resolveNextBufferState(
            nextMediaItemIndex = nextMediaItemIndex,
            repeatMode = repeatMode,
            nextCacheInfo = nextCacheInfo,
            currentTrackFullyCached = currentTrackFullyCached,
            queueFullyCollected = queueFullyCollected,
        ),
    )
}

/**
 * Main-thread binder and Media3 listener, with the previous 500ms position
 * polling. Unbind never calls stopPlayback: audio stays in MediaSessionService.
 */
// Intentionally implements the complete media transport contract in one binder adapter.
@Suppress("TooManyFunctions")
internal class AndroidPlaybackConnection(
    private val context: Context,
    main: CoroutineDispatcher,
) : PlaybackConnection {
    private val mutable = MutableStateFlow(PlaybackSnapshot())
    override val state: StateFlow<PlaybackSnapshot> = mutable
    private val scope = CoroutineScope(SupervisorJob() + main)
    private var service: EnoTonePlaybackService? = null
    private var bound = false
    private var nowPlayingVisible = false
    private var ticker: Job? = null

    /** Fails the connection when bindService succeeded but the service never answers. */
    private var connectTimeout: Job? = null

    /** Consecutive reconnect attempts since the last successful connection; drives backoff. */
    private var connectAttempts = 0

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service?.player?.removeListener(listener)
            service = (binder as? EnoTonePlaybackService.LocalBinder)?.service()
            service?.player?.addListener(listener)
            connectAttempts = 0
            publish()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service?.player?.removeListener(listener)
            service = null
            publish()
            // A killed service unbinds us but leaves `bound` true. Without re-binding the
            // UI stays disconnected forever: taps are ignored and Play does nothing.
            // BIND_AUTO_CREATE restarts the service, so a single retry is enough.
            reattach()
        }
        override fun onBindingDied(name: ComponentName?) {
            // The process hosting the service is gone; the old binding is unusable and
            // must be dropped before a new bindService call.
            service?.player?.removeListener(listener)
            service = null
            publish()
            rebind()
        }
    }

    /** Re-establishes a lost binding without dropping the ticker or the UI snapshot. */
    private fun reattach(): Boolean {
        if (!bound) return false
        runCatching { context.unbindService(connection) }
        bound = false
        service = null
        bindNow()
        return bound
    }

    /**
     * Binds again when there is nothing to unbind, e.g. after bindService returned false or
     * threw: the earlier connect() already owns the ticker, so connect() would return early.
     */
    private fun rebindNow(): Boolean {
        bindNow()
        return bound
    }

    /** Full rebind after the service process died: drop the stale binding, then bind. */
    private fun rebind() {
        ticker?.cancel()
        ticker = null
        runCatching { context.unbindService(connection) }
        bound = false
        connect()
    }

    override fun connect() {
        if (bound || ticker != null) return
        bindNow()
        ticker = scope.launch {
            while (isActive) {
                publish()
                delay(POSITION_POLL_MS)
            }
        }
    }

    private fun bindNow() {
        try {
            bound = context.bindService(
                Intent(context, EnoTonePlaybackService::class.java)
                    .setAction(EnoTonePlaybackService.ACTION_LOCAL_BIND),
                connection, Context.BIND_AUTO_CREATE,
            )
            if (!bound) {
                mutable.value = PlaybackSnapshot(playbackState = "ERROR")
                connectTimeout?.cancel()
                connectTimeout = null
            } else {
                // bindService returning true only means the request was accepted. A
                // foreground-service restriction or a crashed service can leave
                // onServiceConnected pending forever, and the UI would show a frozen
                // snapshot with a spinning ticker and no explanation.
                connectTimeout?.cancel()
                connectTimeout = scope.launch {
                    delay(CONNECT_TIMEOUT_MS)
                    if (service != null) return@launch
                    // Reconnect, but back off: a service that keeps failing must not be
                    // re-bound every three seconds for as long as the app is open.
                    delay(connectBackoffMs(connectAttempts))
                    connectAttempts += 1
                    if (service == null) reattach()
                }
            }
        } catch (_: Exception) {
            mutable.value = PlaybackSnapshot(playbackState = "ERROR")
            connectTimeout?.cancel()
            connectTimeout = null
        }
    }

    override fun disconnect() {
        ticker?.cancel()
        ticker = null
        connectTimeout?.cancel()
        connectTimeout = null
        // A fresh session must start from an immediate attempt, not inherit the previous
        // session's slowest pause.
        connectAttempts = 0
        nowPlayingVisible = false
        service?.player?.removeListener(listener)
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        service = null
        // The container is process-scoped; future ViewModels can reconnect.
        mutable.value = PlaybackSnapshot()
    }

    override fun setNowPlayingVisible(visible: Boolean) {
        if (nowPlayingVisible == visible) return
        nowPlayingVisible = visible
        publish()
    }

    private fun bufferingSnapshot(
        active: EnoTonePlaybackService,
        player: Player,
    ): PlaybackBufferingSnapshot {
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val durationMs = active.displayDurationMs.coerceAtLeast(0L)
        val knownDurationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val currentTrackFullyCached = active.currentTrackFullyCached
        val queueFullyCollected = active.queueSize > 0 &&
            active.loadedQueueSize >= active.queueSize
        val nextCacheInfo = active.nextCacheSnapshot()
        val repeatMode = active.repeatMode
        val nextMediaItemIndex = player.nextMediaItemIndex
        return resolvePlaybackBufferingSnapshot(
            positionMs = positionMs,
            durationMs = durationMs,
            knownDurationMs = knownDurationMs,
            nowPlayingVisible = nowPlayingVisible,
            readPlayerBufferedPositionMs = { player.bufferedPosition },
            currentTrackFullyCached = currentTrackFullyCached,
            repeatMode = repeatMode,
            nextMediaItemIndex = nextMediaItemIndex,
            nextCacheInfo = nextCacheInfo,
            queueFullyCollected = queueFullyCollected,
        )
    }

    private fun publish() {
        val active = service ?: run {
            // Preserve a bind error instead of replacing it with IDLE.
            if (mutable.value.playbackState != "ERROR") mutable.value = PlaybackSnapshot()
            return
        }
        val p = active.player
        val error = p.playerError
        val buffering = bufferingSnapshot(active, p)
        val queueOrder = active.queueSnapshot
        mutable.value = PlaybackSnapshot(
            connected = true,
            playbackState = playbackLabel(p),
            currentTrackId = p.currentMediaItem?.mediaId,
            sourceKey = active.activeSourceKey,
            title = p.currentMediaItem?.mediaMetadata?.title?.toString(),
            artist = p.currentMediaItem?.mediaMetadata?.artist?.toString(),
            positionMs = buffering.positionMs,
            bufferedPositionMs = buffering.bufferedPositionMs,
            // Includes the saved duration of a restored, not yet prepared item.
            durationMs = buffering.durationMs,
            knownDurationMs = buffering.knownDurationMs,
            canSeek = active.canSeekCurrent,
            queuePosition = queueOrder?.currentIndex?.plus(1) ?: 0,
            queueTotal = active.queueSize,
            loadedQueueSources = active.loadedQueueSize,
            queueOrder = queueOrder,
            repeatMode = buffering.repeatMode,
            shuffleEnabled = active.shuffleEnabled,
            hasPrevious = active.hasPrevious,
            hasNext = buffering.hasNext,
            errorCode = error?.errorCode ?: 0,
            errorReadIssue = if (error == null) PlaybackDataIssue.UNKNOWN
                else PlaybackDataIssueClassifier.classify(error),
            setupTimings = active.setupTimings(),
            handoffInfo = active.handoffSnapshot(),
            cacheInfo = active.cacheSnapshot(),
            nextCacheInfo = buffering.nextCacheInfo,
            nextBufferState = buffering.nextBufferState,
        )
    }

    override fun playQueue(email: String, password: String, queue: List<QueueTrack>,
                           selectedId: String, useCache: Boolean, sourceKey: String) {
        val active = checkNotNull(service) { "Playback service disconnected" }
        active.playQueue(email, password, queue, selectedId, useCache, sourceKey)
        publish()
    }

    override fun restoreQueue(email: String, password: String, queue: List<QueueTrack>,
                              saved: PersistedQueueState, useCache: Boolean,
                              filtered: Boolean): Boolean {
        val restored = service?.restoreQueue(email, password, queue, saved, useCache, filtered)
            ?: false
        publish()
        return restored
    }

    override fun stopPlayback(): Boolean {
        val active = service ?: return false
        active.stopPlayback()
        publish()
        return true
    }

    override fun stopPlaybackAndEraseData(): CompletableFuture<Unit> {
        // Erasing on the service IO lane while the cache may be open would race with
        // Media3. When the binding is gone the caller falls back to its own file-level
        // cleanup, so report that instead of pretending the erase happened.
        val active = service ?: return CompletableFuture<Unit>().apply {
            // failedFuture needs API 31; minSdk is 26.
            completeExceptionally(IllegalStateException("playback service not bound"))
        }
        val erased = active.stopPlaybackAndEraseData()
        publish()
        return erased
    }

    override fun playPause() {
        service?.let {
            if (it.player.isPlaying || it.player.playbackState == Player.STATE_BUFFERING)
                it.pause() else it.resume()
        }
        publish()
    }
    override fun retryCurrent(): Boolean {
        val active = service
        if (active != null) {
            val retried = active.retryCurrent()
            publish()
            return retried
        }
        // No service: retrying a track is impossible, but the user's tap must still do
        // something. Reconnect instead of returning false, which left the error sheet open
        // with a button that could never succeed. connect() would return early here — the
        // ticker from the first connect() is still alive — so the reconnect goes through
        // reattach(), which binds unconditionally.
        connectAttempts = 0
        val reconnected = reattach() || rebindNow()
        publish()
        return reconnected
    }
    override fun playNext() { service?.playNext(); publish() }
    override fun playPrevious() { service?.playPrevious(); publish() }
    override fun skipToQueuePosition(position: Int) {
        service?.skipToQueuePosition(position)
        publish()
    }
    override fun insertNext(tracks: List<QueueTrack>): QueueEditResult {
        val result = service?.insertNext(tracks)
            ?: QueueEditResult(QueueEditStatus.UNAVAILABLE)
        publish()
        return result
    }
    override fun append(tracks: List<QueueTrack>): QueueEditResult {
        val result = service?.append(tracks)
            ?: QueueEditResult(QueueEditStatus.UNAVAILABLE)
        publish()
        return result
    }
    override fun cycleRepeatMode() { service?.cycleRepeatMode(); publish() }
    override fun toggleShuffle() { service?.toggleShuffle(); publish() }
    override fun seekToFraction(fraction: Float) { service?.seekToFraction(fraction); publish() }
}
