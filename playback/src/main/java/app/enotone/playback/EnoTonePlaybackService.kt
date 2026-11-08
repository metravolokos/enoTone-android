package app.enotone.playback

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * A single foreground Media3 session for user-started playback.
 *
 * The app-only local binder passes selected-track credentials directly in RAM,
 * never through intents, PendingIntents, session extras, Room, notifications or URLs.
 * The Media3 session handles the notification/media buttons while playback runs.
 */
class EnoTonePlaybackService : MediaSessionService() {
    private lateinit var playback: PreviewAudioPlayback
    private lateinit var session: MediaSession
    private val localBinder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private data class Credentials(val email: String, val password: String)
    private var credentials: Credentials? = null
    private var queue: PlaybackQueue? = null
    private val completionListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                // Defer to avoid re-entering Media3 inside its listener callback.
                mainHandler.post {
                    if (playback.player.playbackState == Player.STATE_ENDED) {
                        playNext()
                    }
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun service(): EnoTonePlaybackService = this@EnoTonePlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        playback = PreviewAudioPlayback(this)
        playback.player.addListener(completionListener)
        session = MediaSession.Builder(this, playback.player).build()
        // Local in-process binding bypasses onGetSession: register explicitly
        // so Media3 can manage the foreground notification and service lifecycle.
        addSession(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_LOCAL_BIND) localBinder else super.onBind(intent)

    val player: Player
        get() = playback.player

    /** Starts only the selected item; the remaining context lives in RAM. */
    fun playQueue(
        email: String,
        password: String,
        tracks: List<QueueTrack>,
        selectedId: String,
    ) {
        require(email.contains('@') && password.isNotBlank()) {
            "Playback credentials required"
        }
        val selectedQueue = PlaybackQueue.create(tracks, selectedId)
        // Reject unexpected paths before replacing the working queue.
        selectedQueue.current.let { WebDavPlaybackTarget.url(it.encodedPath) }
        val selected = selectedQueue.current
        playback.play(email, password, selected.encodedPath,
            selected.remoteSize, selected.remoteEtag, selected.title)
        queue = selectedQueue
        credentials = Credentials(email, password)
    }

    val hasNext: Boolean get() = queue?.hasNext == true
    val hasPrevious: Boolean get() = queue?.hasPrevious == true
    val queueSize: Int get() = queue?.size ?: 0
    val queuePosition: Int get() = queue?.position ?: 0

    fun playNext() {
        val active = credentials ?: return
        val ctx = queue ?: return
        if (!ctx.hasNext) return
        val next = ctx.next() ?: return
        playback.play(active.email, active.password, next.encodedPath,
            next.remoteSize, next.remoteEtag, next.title)
    }

    fun playPrevious() {
        val active = credentials ?: return
        val ctx = queue ?: return
        val prev = ctx.previous() ?: return
        playback.play(active.email, active.password, prev.encodedPath,
            prev.remoteSize, prev.remoteEtag, prev.title)
    }

    fun pause() = playback.pause()
    fun resume() = playback.resume()
    fun stopPlayback() {
        playback.stop()
        queue = null
        credentials = null
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        playback.player.removeListener(completionListener)
        credentials = null
        queue = null
        session.release()
        playback.close()
        super.onDestroy()
    }

    companion object {
        const val ACTION_LOCAL_BIND = "app.enotone.android.action.LOCAL_PLAYBACK_BIND"
    }
}
