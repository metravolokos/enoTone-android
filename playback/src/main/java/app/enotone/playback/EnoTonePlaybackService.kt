package app.enotone.playback

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import java.util.concurrent.Executors
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Media3 owns the playlist timeline and its built-in next/previous commands.
 * System UI and Bluetooth headsets can use standard Player actions. Credentials
 * and metadata remain only in the private in-process service and DataSources.
 */
class EnoTonePlaybackService : MediaSessionService() {
    private lateinit var playback: PreviewAudioPlayback
    private lateinit var session: MediaSession
    private val localBinder = LocalBinder()
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { QueueStateFileStore(applicationContext) }
    private val throttle = QueueWriteThrottle(QueueMonotonicClock {
        SystemClock.elapsedRealtime()
    })
    private var pendingSave: Runnable? = null
    private val periodicSave = object : Runnable {
        override fun run() {
            if (playback.player.isPlaying) saveSnapshot()
            handler.postDelayed(this, 12_000L)
        }
    }
    private val persistenceListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) saveSnapshot(urgent = true)
        }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) saveSnapshot()
        }
        override fun onRepeatModeChanged(repeatMode: Int) = saveSnapshot(urgent = true)
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) =
            saveSnapshot(urgent = true)
    }

    private fun saveSnapshot(urgent: Boolean = false) {
        val snapshot = playback.snapshotQueueState(System.currentTimeMillis()) ?: return
        val delay = throttle.delayMs(urgent)
        pendingSave?.let(handler::removeCallbacks)
        pendingSave = null
        if (delay > 0L) {
            val task = Runnable { pendingSave = null; saveSnapshot() }
            pendingSave = task
            handler.postDelayed(task, delay)
        } else {
            throttle.markWritten()
            io.execute {
                try { store.write(snapshot) } catch (_: Exception) {
                    // Never log file contents, track names or exceptions.
                }
            }
        }
    }

    /** Call from Dispatchers.IO. A malformed or absent JSON snapshot is null. */
    fun readSavedQueue(): PersistedQueueState? = store.read()

    /** The caller verifies the opt-in vault and matches the hashed Room source. */
    fun restoreQueue(
        email: String,
        password: String,
        tracks: List<QueueTrack>,
        state: PersistedQueueState,
        useTemporaryCache: Boolean,
    ): Boolean {
        if (playback.logicalQueueSize > 0 || tracks.isEmpty() ||
            tracks.map { it.id } != state.trackIds) return false
        playback.restoreQueue(email, password, tracks, state, useTemporaryCache)
        saveSnapshot(urgent = true)
        return true
    }

    inner class LocalBinder : Binder() {
        fun service(): EnoTonePlaybackService = this@EnoTonePlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        playback = PreviewAudioPlayback(this)
        playback.player.addListener(persistenceListener)
        handler.postDelayed(periodicSave, 12_000L)
        session = MediaSession.Builder(this, playback.player).build()
        // Local in-process binding bypasses onGetSession; explicitly register.
        addSession(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_LOCAL_BIND) localBinder else super.onBind(intent)

    val player: Player
        get() = playback.player

    /** The list and Basic Auth are held in RAM by Media3 sources only. */
    fun playQueue(
        email: String,
        password: String,
        tracks: List<QueueTrack>,
        selectedId: String,
        useTemporaryCache: Boolean,
        sourceKey: String,
    ) {
        playback.playQueue(email, password, tracks, selectedId, useTemporaryCache, sourceKey)
        saveSnapshot(urgent = true)
    }

    val hasNext: Boolean get() = playback.player.hasNextMediaItem()
    val hasPrevious: Boolean get() = playback.player.hasPreviousMediaItem()
    /** Full logical context remains 3590 even while Media3 fills in chunks. */
    val queueSize: Int get() = playback.logicalQueueSize
    val queuePosition: Int get() = playback.logicalQueuePosition
    val loadedQueueSize: Int get() = playback.loadedQueueSize
    fun setupTimings(): PlaybackSetupTimings = playback.setupTimings()
    fun handoffSnapshot(): HandoffSnapshot? = playback.handoffSnapshot()

    fun playNext() {
        if (hasNext) {
            playback.noteNextButton()
            playback.player.seekToNextMediaItem()
            playback.resumeAfterManualSkip()
        }
    }

    fun playPrevious() {
        if (hasPrevious) {
            playback.notePreviousButton()
            playback.player.seekToPreviousMediaItem()
            playback.resumeAfterManualSkip()
        }
    }

    fun cacheSnapshot(): TransientCacheStatus = playback.cacheSnapshot()
    fun nextCacheSnapshot(): TransientCacheStatus = playback.nextCacheSnapshot()

    val repeatMode: Int get() = playback.player.repeatMode
    val shuffleEnabled: Boolean get() = playback.player.shuffleModeEnabled

    /** Let ExoPlayer and MediaSession own the actual repeat and shuffle order. */
    fun cycleRepeatMode() {
        playback.player.repeatMode = PlaybackModes.nextRepeat(playback.player.repeatMode)
    }

    fun toggleShuffle() {
        playback.player.shuffleModeEnabled = !playback.player.shuffleModeEnabled
    }

    /**
     * Seek within the current Media3 item only. In M5 cache mode an already
     * committed span is read locally; unavailable bytes use the same strictly
     * validated HTTPS Range DataSource. No new WebDAV listing is triggered.
     */
    val canSeekCurrent: Boolean
        get() = playback.player.isCommandAvailable(
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
            PlaybackSeekPolicy.isKnownDuration(playback.player.duration)

    fun seekToFraction(fraction: Float): Boolean {
        val player = playback.player
        if (!player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            return false
        }
        val target = PlaybackSeekPolicy.positionForFraction(player.duration, fraction)
            ?: return false
        player.seekTo(target)
        return true
    }

    fun pause() = playback.pause()
    fun resume() = playback.resume()
    fun stopPlayback() {
        pendingSave?.let(handler::removeCallbacks)
        pendingSave = null
        playback.stop()
        io.execute {
            try { store.clear() } catch (_: Exception) { }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveSnapshot(urgent = true)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(periodicSave)
        pendingSave?.let(handler::removeCallbacks)
        saveSnapshot(urgent = true)
        playback.player.removeListener(persistenceListener)
        session.release()
        playback.close()
        io.shutdown() // Flush queued writes in order; never block the main thread.
        super.onDestroy()
    }

    companion object {
        const val ACTION_LOCAL_BIND = "app.enotone.android.action.LOCAL_PLAYBACK_BIND"
    }
}
