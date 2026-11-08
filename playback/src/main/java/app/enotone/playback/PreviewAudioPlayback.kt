package app.enotone.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.nio.charset.StandardCharsets

/**
 * One-track, foreground-only Media3 proof-of-playback; no session/service yet.
 *
 * Auth credentials stay in process memory, are not saved or placed in the URI.
 * The HTTP transport disables all redirects so Authorization never follows a
 * 302 to an unrelated host. HTTPS host/path are validated before every play.
 */
@OptIn(UnstableApi::class)
class PreviewAudioPlayback(
    context: Context,
    email: String,
    appPassword: String,
) : AutoCloseable {
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    val player: ExoPlayer

    init {
        require(email.contains('@') && appPassword.isNotBlank()) {
            "Playback credentials required"
        }
        val factory = OkHttpDataSource.Factory(httpClient)
            .setDefaultRequestProperties(
                mapOf(
                    "Authorization" to Credentials.basic(
                        email.trim(), appPassword, StandardCharsets.UTF_8),
                    "Accept-Encoding" to "identity",
                )
            )

        player = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(factory))
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
    }

    fun play(encodedPath: String) {
        val safeUrl = WebDavPlaybackTarget.url(encodedPath)
        player.setMediaItem(MediaItem.fromUri(safeUrl))
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
        httpClient.connectionPool.evictAll()
        httpClient.dispatcher.executorService.shutdown()
    }
}
