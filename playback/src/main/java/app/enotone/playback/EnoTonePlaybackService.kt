package app.enotone.playback

import android.content.Intent
import android.os.Binder
import android.os.IBinder
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

    inner class LocalBinder : Binder() {
        fun service(): EnoTonePlaybackService = this@EnoTonePlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        playback = PreviewAudioPlayback(this)
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
    ) {
        playback.playQueue(email, password, tracks, selectedId, useTemporaryCache)
    }

    val hasNext: Boolean get() = playback.player.hasNextMediaItem()
    val hasPrevious: Boolean get() = playback.player.hasPreviousMediaItem()
    /** Full logical context remains 3590 even while Media3 fills in chunks. */
    val queueSize: Int get() = playback.logicalQueueSize
    val queuePosition: Int get() = playback.logicalQueuePosition
    val loadedQueueSize: Int get() = playback.loadedQueueSize
    fun setupTimings(): PlaybackSetupTimings = playback.setupTimings()

    fun playNext() {
        if (hasNext) playback.player.seekToNextMediaItem()
    }

    fun playPrevious() {
        if (hasPrevious) playback.player.seekToPreviousMediaItem()
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

    fun pause() = playback.pause()
    fun resume() = playback.resume()
    fun stopPlayback() = playback.stop()

    override fun onDestroy() {
        session.release()
        playback.close()
        super.onDestroy()
    }

    companion object {
        const val ACTION_LOCAL_BIND = "app.enotone.android.action.LOCAL_PLAYBACK_BIND"
    }
}
