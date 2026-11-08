package app.enotone.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource

/**
 * One-track, foreground-only Media3 proof-of-playback; no media service yet.
 * Uses a strict 206/Content-Range HTTP source without OkHttp/GMS runtime.
 * Credentials remain in memory and never appear in the audio URI or Room.
 */
@OptIn(UnstableApi::class)
class PreviewAudioPlayback(
    context: Context,
    private val email: String,
    private val appPassword: String,
) : AutoCloseable {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            repeatMode = Player.REPEAT_MODE_OFF
        }

    init {
        require(email.contains('@') && appPassword.isNotBlank()) {
            "Playback credentials required"
        }
    }

    fun play(encodedPath: String, remoteSize: Long, remoteEtag: String?) {
        val safeUrl = WebDavPlaybackTarget.url(encodedPath)
        val factory = WebDavMedia3DataSource.Factory(
            email, appPassword, remoteSize, remoteEtag)
        val source = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(safeUrl))
        player.setMediaSource(source)
        player.prepare()
        player.play()
    }

    fun pause() = player.pause()

    fun resume() = player.play()

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    override fun close() {
        stop()
        player.release()
    }
}
