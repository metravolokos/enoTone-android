package app.enotone.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource

/**
 * Media3 player used by private MediaSessionService with native playlist controls.
 * Uses a strict 206/Content-Range HTTP source without OkHttp/GMS runtime.
 * Credentials remain in memory and never appear in the audio URI or Room.
 */
@OptIn(UnstableApi::class)
class PreviewAudioPlayback(
    context: Context,
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

    /**
     * Give Media3 the real local playlist, so Android notification and Bluetooth
     * use standard seekToNext/Previous media-item commands, not custom actions.
     * Only Media3 decides which selected item to open, and each open is checked
     * by WebDavMedia3DataSource against its own saved size and ETag.
     */
    fun playQueue(
        email: String,
        appPassword: String,
        tracks: List<QueueTrack>,
        selectedId: String,
    ) {
        val queue = PlaybackQueue.create(tracks, selectedId)
        val factory = WebDavMedia3DataSource.Factory(email, appPassword, queue.items)
        val mediaFactory = ProgressiveMediaSource.Factory(factory)
        val sources = queue.items.map { item ->
            mediaFactory.createMediaSource(
                MediaItem.Builder()
                    .setMediaId(item.id)
                    .setUri(WebDavPlaybackTarget.url(item.encodedPath))
                    .setMediaMetadata(
                        MediaMetadata.Builder().setTitle(item.title).build())
                    .build())
        }
        player.setMediaSources(sources, queue.position, C.TIME_UNSET)
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
