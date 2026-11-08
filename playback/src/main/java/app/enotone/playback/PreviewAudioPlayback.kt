package app.enotone.playback

import android.content.Context
import androidx.media3.datasource.DataSource
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
    private val temporaryCache = TransientMediaCache(context.applicationContext)
    private var activeRoutes: Map<String, QueueTrack> = emptyMap()
    private var upstreamFactory: DataSource.Factory? = null
    private val cacheHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private data class PendingCache(
        val mediaId: String,
        val key: String,
        val uri: android.net.Uri,
        val size: Long,
        val upstream: DataSource.Factory,
    )
    private var pendingCache: PendingCache? = null
    private var scheduledWriter: Runnable? = null

    private fun clearScheduledWriter() {
        scheduledWriter?.let(cacheHandler::removeCallbacks)
        scheduledWriter = null
    }

    private fun scheduleAfterFirstBuffer() {
        val pending = pendingCache ?: return
        if (scheduledWriter != null) return
        if (!CacheStartPolicy.mayStart(
                player.playbackState,
                player.currentMediaItem?.mediaId,
                pending.mediaId,
            )) return
        val task = Runnable {
            scheduledWriter = null
            if (pendingCache !== pending) return@Runnable
            if (!CacheStartPolicy.mayStart(
                    player.playbackState,
                    player.currentMediaItem?.mediaId,
                    pending.mediaId,
                )) return@Runnable
            pendingCache = null
            // Crucially this does NOT consult playWhenReady. The worker keeps
            // downloading even when Pause is pressed after initial READY.
            temporaryCache.prefetchCurrent(
                pending.key, pending.uri, pending.size, pending.upstream)
        }
        scheduledWriter = task
        cacheHandler.postDelayed(task, CacheStartPolicy.START_DELAY_MS)
    }

    private val cacheTransitionListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            clearScheduledWriter()
            pendingCache = null
            temporaryCache.stopCurrent()
            val selected = mediaItem?.let { activeRoutes[it.mediaId] }
            val source = upstreamFactory
            val config = mediaItem?.localConfiguration
            if (selected == null || source == null || config == null) return
            pendingCache = PendingCache(
                mediaItem.mediaId, TransientCachePolicy.key(selected),
                config.uri, selected.remoteSize, source)
            // Wait for this item's first READY before competing for WebDAV.
            // This also handles a transition into an already-prepared item.
            scheduleAfterFirstBuffer()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                scheduleAfterFirstBuffer()
            } else if (state == Player.STATE_BUFFERING) {
                // A rebuffering stream takes priority over full-track download.
                clearScheduledWriter()
            }
        }
    }
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
            addListener(cacheTransitionListener)
        }

    fun cacheSnapshot(): TransientCacheStatus = temporaryCache.snapshot()

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
        val mediaFactory = ProgressiveMediaSource.Factory(
            temporaryCache.forPlayback(factory))
        // Route mapping is ready before setMediaSources raises the transition.
        activeRoutes = queue.items.associateBy { it.id }
        upstreamFactory = factory
        val sources = queue.items.map { item ->
            mediaFactory.createMediaSource(
                MediaItem.Builder()
                    .setMediaId(item.id)
                    .setUri(WebDavPlaybackTarget.url(item.encodedPath))
                    .setCustomCacheKey(TransientCachePolicy.key(item))
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
        clearScheduledWriter()
        pendingCache = null
        temporaryCache.stopCurrent()
        player.stop()
        player.clearMediaItems()
        activeRoutes = emptyMap()
        upstreamFactory = null
    }

    override fun close() {
        stop()
        player.removeListener(cacheTransitionListener)
        player.release()
        temporaryCache.close()
    }
}
