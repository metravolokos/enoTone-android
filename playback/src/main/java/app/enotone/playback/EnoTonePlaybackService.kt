package app.enotone.playback

import android.content.Intent
import android.os.Binder
import android.os.IBinder
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

    inner class LocalBinder : Binder() {
        fun service(): EnoTonePlaybackService = this@EnoTonePlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        playback = PreviewAudioPlayback(this)
        session = MediaSession.Builder(this, playback.player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_LOCAL_BIND) localBinder else super.onBind(intent)

    val player: Player
        get() = playback.player

    fun play(
        email: String,
        password: String,
        encodedPath: String,
        remoteSize: Long,
        etag: String?,
        title: String,
    ) {
        playback.play(email, password, encodedPath, remoteSize, etag, title)
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
