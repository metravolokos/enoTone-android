package app.enotone.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.media3.common.Player
import app.enotone.playback.CachePhase
import app.enotone.playback.EnoTonePlaybackService
import app.enotone.playback.HandoffSnapshot
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.PlaybackDataIssue
import app.enotone.playback.PlaybackDataIssueClassifier
import app.enotone.playback.PlaybackSetupTimings
import app.enotone.playback.QueueTrack
import app.enotone.playback.TransientCacheStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// 500 ms preserves the existing position/seek polling cadence without a CPU spin.
private const val POSITION_POLL_MS = 500L

/** No service/binder reference escapes to the ViewModel or the composable. */
internal data class PlaybackSnapshot(
    val connected: Boolean = false,
    val playbackState: String = "IDLE",
    val currentTrackId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val canSeek: Boolean = false,
    val queuePosition: Int = 0,
    val queueTotal: Int = 0,
    val loadedQueueSources: Int = 0,
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
)

/** Service connection is active only for the owning ViewModel's lifetime. */
// Queue actions carry credentials, source and selection as one explicit call.
@Suppress("LongParameterList")
internal interface PlaybackConnection {
    val state: StateFlow<PlaybackSnapshot>
    fun connect()
    fun disconnect()
    fun playQueue(email: String, password: String, queue: List<QueueTrack>,
                  selectedId: String, useCache: Boolean, sourceKey: String)
    fun restoreQueue(email: String, password: String, queue: List<QueueTrack>,
                     saved: PersistedQueueState, useCache: Boolean, filtered: Boolean): Boolean
    /** Returns false when the local MediaSession binder is unavailable. */
    fun stopPlayback(): Boolean
    fun playPause()
    fun playNext()
    fun playPrevious()
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
    private var ticker: Job? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service?.player?.removeListener(listener)
            service = (binder as? EnoTonePlaybackService.LocalBinder)?.service()
            service?.player?.addListener(listener)
            publish()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service?.player?.removeListener(listener)
            service = null
            publish()
        }
    }

    override fun connect() {
        if (bound || ticker != null) return
        try {
            bound = context.bindService(
                Intent(context, EnoTonePlaybackService::class.java)
                    .setAction(EnoTonePlaybackService.ACTION_LOCAL_BIND),
                connection, Context.BIND_AUTO_CREATE,
            )
            if (!bound) mutable.value = PlaybackSnapshot(playbackState = "ERROR")
        } catch (_: Exception) {
            mutable.value = PlaybackSnapshot(playbackState = "ERROR")
        }
        ticker = scope.launch {
            while (isActive) {
                publish()
                delay(POSITION_POLL_MS)
            }
        }
    }

    override fun disconnect() {
        ticker?.cancel()
        ticker = null
        service?.player?.removeListener(listener)
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        service = null
        // The container is process-scoped; future ViewModels can reconnect.
        mutable.value = PlaybackSnapshot()
    }

    private fun publish() {
        val active = service ?: run {
            // Preserve a bind error instead of replacing it with IDLE.
            if (mutable.value.playbackState != "ERROR") mutable.value = PlaybackSnapshot()
            return
        }
        val p = active.player
        val error = p.playerError
        mutable.value = PlaybackSnapshot(
            connected = true,
            playbackState = playbackLabel(p),
            currentTrackId = p.currentMediaItem?.mediaId,
            title = p.currentMediaItem?.mediaMetadata?.title?.toString(),
            artist = p.currentMediaItem?.mediaMetadata?.artist?.toString(),
            positionMs = p.currentPosition.coerceAtLeast(0L),
            // Includes the saved duration of a restored, not yet prepared item.
            durationMs = active.displayDurationMs,
            canSeek = active.canSeekCurrent,
            queuePosition = active.queuePosition + 1,
            queueTotal = active.queueSize,
            loadedQueueSources = active.loadedQueueSize,
            repeatMode = active.repeatMode,
            shuffleEnabled = active.shuffleEnabled,
            hasPrevious = active.hasPrevious,
            hasNext = active.hasNext,
            errorCode = error?.errorCode ?: 0,
            errorReadIssue = if (error == null) PlaybackDataIssue.UNKNOWN
                else PlaybackDataIssueClassifier.classify(error),
            setupTimings = active.setupTimings(),
            handoffInfo = active.handoffSnapshot(),
            cacheInfo = active.cacheSnapshot(),
            nextCacheInfo = active.nextCacheSnapshot(),
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

    override fun playPause() {
        service?.let {
            if (it.player.isPlaying || it.player.playbackState == Player.STATE_BUFFERING)
                it.pause() else it.resume()
        }
        publish()
    }
    override fun playNext() { service?.playNext(); publish() }
    override fun playPrevious() { service?.playPrevious(); publish() }
    override fun cycleRepeatMode() { service?.cycleRepeatMode(); publish() }
    override fun toggleShuffle() { service?.toggleShuffle(); publish() }
    override fun seekToFraction(fraction: Float) { service?.seekToFraction(fraction); publish() }
}
