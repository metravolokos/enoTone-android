package app.enotone.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ShuffleOrder

/**
 * Media3 player used by private MediaSessionService with native playlist controls.
 * Uses a strict 206/Content-Range HTTP source without OkHttp/GMS runtime.
 * Credentials remain in memory and never appear in the audio URI or Room.
 */
/** Numeric timings only; no track paths, URLs, credentials or tags. */
data class PlaybackSetupTimings(
    val queueMs: Long,
    val routesAndCacheMs: Long,
    val seedSourcesMs: Long,
    val setSourcesMs: Long,
    val prepareAndPlayMs: Long,
)

@OptIn(UnstableApi::class)
class PreviewAudioPlayback(
    context: Context,
) : AutoCloseable {
    // Lazy: in baseline mode neither initialize nor touch the disk cache.
    private val applicationContext = context.applicationContext
    private var temporaryCache: TransientMediaCache? = null
    private var cacheEnabledForQueue = false
    private var activeRoutes: Map<String, QueueTrack> = emptyMap()
    private var upstreamFactory: DataSource.Factory? = null
    private var fullyCachedCurrentId: String? = null
    private var scheduledNextId: String? = null
    private val handoffTracker = PlaybackHandoffTracker()
    // A single guarded recovery per ended item. Do not override Pause or
    // an audio focus suppression.
    private var lastEndRecoveryId: String? = null
    fun handoffSnapshot(): HandoffSnapshot? = handoffTracker.snapshot()

    fun noteNextButton() = handoffTracker.beginButtonSkip(
        HandoffOrigin.NEXT_BUTTON, SystemClock.elapsedRealtime())

    fun notePreviousButton() = handoffTracker.beginButtonSkip(
        HandoffOrigin.PREVIOUS_BUTTON, SystemClock.elapsedRealtime())

    /**
     * Registered before the cache listener: capture a prefetched-next status
     * before that listener cancels the former current/next downloads.
     */
    private val handoffListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val preparedNext = mediaItem != null &&
                mediaItem.mediaId == scheduledNextId &&
                temporaryCache?.nextSnapshot()?.phase == CachePhase.COMPLETE
            val nowMs = SystemClock.elapsedRealtime()
            handoffTracker.onItemChanged(
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                preparedNext, nowMs)
            // Media3 can advance to an already prepared item without emitting
            // another READY / isPlaying change, so sample current state here.
            if (player.playbackState == Player.STATE_READY) {
                handoffTracker.onReady(nowMs)
                if (player.isPlaying) handoffTracker.onPlaying(nowMs)
            }
        }
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> handoffTracker.onBuffering(SystemClock.elapsedRealtime())
                Player.STATE_READY -> handoffTracker.onReady(SystemClock.elapsedRealtime())
                Player.STATE_ENDED -> scheduleEndRecoveryIfNeeded()
            }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) handoffTracker.onPlaying(SystemClock.elapsedRealtime())
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            handoffTracker.onError()
        }
    }

    /** Only resolve the upcoming file after the current is fully cached. */
    private fun refreshNextPrefetch() {
        if (!cacheEnabledForQueue) return
        val currentId = player.currentMediaItem?.mediaId ?: return
        if (fullyCachedCurrentId != currentId) return
        // A partially staged timeline has an unstable shuffle/next decision.
        // Do not spend network bytes on a candidate that may change as the
        // remaining 3590 local MediaSources are added. Retry on timeline event.
        if (player.mediaItemCount < expectedQueueSize) {
            if (scheduledNextId != null) {
                temporaryCache?.cancelNext()
                scheduledNextId = null
            }
            return
        }
        // Media3's explicit manual-skip index need not mean automatic repeat-one.
        // Never start a different GET while the player repeats one track.
        if (player.repeatMode == Player.REPEAT_MODE_ONE) {
            temporaryCache?.cancelNext()
            scheduledNextId = null
            return
        }
        val nextIndex = player.nextMediaItemIndex
        val candidateId = if (nextIndex != C.INDEX_UNSET &&
            nextIndex in 0 until player.mediaItemCount) {
            player.getMediaItemAt(nextIndex).mediaId
        } else null
        val next = NextPrefetchSelection.select(currentId, candidateId, activeRoutes)
        if (next?.id == scheduledNextId) return
        temporaryCache?.cancelNext()
        scheduledNextId = null
        if (next == null) return
        val upstream = upstreamFactory ?: return
        val nextUri = player.getMediaItemAt(nextIndex).localConfiguration?.uri ?: return
        scheduledNextId = next.id
        // Same read-only cache and per-item WebDAV Range metadata as playback.
        temporaryCache?.prefetchNext(
            TransientCachePolicy.key(next), nextUri, next.remoteSize, upstream)
    }
    private val cacheHandler = Handler(Looper.getMainLooper())

    /**
     * Normal Media3 auto-advance needs no intervention. If a real next item
     * exists but the player unexpectedly enters ENDED with playWhenReady, make
     * one deferred, rechecked attempt. Explicit Pause/focus loss stays paused.
     */
    private fun scheduleEndRecoveryIfNeeded() {
        val endedId = player.currentMediaItem?.mediaId ?: return
        if (lastEndRecoveryId == endedId || !canRecoverEnded()) return
        cacheHandler.post {
            if (player.currentMediaItem?.mediaId != endedId ||
                lastEndRecoveryId == endedId || !canRecoverEnded()) return@post
            lastEndRecoveryId = endedId
            player.seekToNextMediaItem()
            resumeAfterManualSkip()
        }
    }

    private fun canRecoverEnded(): Boolean =
        PlaybackContinuationPolicy.shouldAutoRecover(
            ended = player.playbackState == Player.STATE_ENDED,
            playWhenReady = player.playWhenReady,
            hasNext = player.hasNextMediaItem(),
            repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE,
            unsuppressed = player.playbackSuppressionReason ==
                Player.PLAYBACK_SUPPRESSION_REASON_NONE,
        )

    private var pendingPlaylist: StagedPlaylistPlan? = null
    private var pendingMediaFactory: ProgressiveMediaSource.Factory? = null
    private var scheduledPlaylistFill: Runnable? = null
    private var expectedQueueSize = 0
    private var mediaIdIndices: Map<String, Int> = emptyMap()
    private var originalTrackIds: List<String> = emptyList()
    private var sourceKeyForQueue: String? = null
    private var configuredShuffle: IntArray? = null
    private var stableShuffleApplied = false
    private var restoringShuffle = false
    private var deferredPrepare = false

    private fun newShuffleOrder(size: Int): IntArray {
        val order = ShuffleOrder.DefaultShuffleOrder(size, System.nanoTime())
        val indices = IntArray(size)
        var current = order.firstIndex
        for (i in indices.indices) {
            indices[i] = current
            current = order.getNextIndex(current)
        }
        return indices
    }

    /** Staged Media3 order is incomplete; install an exact full permutation once. */
    private fun applyStableShuffleIfComplete() {
        if (stableShuffleApplied || !player.shuffleModeEnabled ||
            player.mediaItemCount != expectedQueueSize) return
        val indices = configuredShuffle ?: return
        stableShuffleApplied = true
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(indices.copyOf(), System.nanoTime()))
    }

    fun snapshotQueueState(savedAtEpochMs: Long): PersistedQueueState? {
        val sourceKey = sourceKeyForQueue ?: return null
        val currentId = player.currentMediaItem?.mediaId ?: return null
        val result = PersistedQueueState(
            trackIds = originalTrackIds,
            selectedTrackId = currentId,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            repeatMode = player.repeatMode,
            shuffleEnabled = player.shuffleModeEnabled,
            shuffledIndices = if (player.shuffleModeEnabled) configuredShuffle?.copyOf() else null,
            sourceKey = sourceKey, savedAtEpochMs = savedAtEpochMs,
        )
        return result.takeIf(QueueStateCodec::isValid)
    }
    private var latestSetup = PlaybackSetupTimings(0L, 0L, 0L, 0L, 0L)

    val logicalQueueSize: Int get() = expectedQueueSize
    val logicalQueuePosition: Int get() {
        val id = player.currentMediaItem?.mediaId ?: return 0
        return mediaIdIndices[id] ?: 0
    }
    val loadedQueueSize: Int get() =
        pendingPlaylist?.loadedCount ?: player.mediaItemCount
    fun setupTimings(): PlaybackSetupTimings = latestSetup

    private fun cancelPlaylistFill() {
        scheduledPlaylistFill?.let(cacheHandler::removeCallbacks)
        scheduledPlaylistFill = null
        pendingPlaylist = null
        pendingMediaFactory = null
    }

    /** Build a bounded portion of the full Media3 timeline after first READY. */
    private fun stagePlaylistAfterReady() {
        val plan = pendingPlaylist ?: return
        val factory = pendingMediaFactory ?: return
        if (scheduledPlaylistFill != null || player.playbackState != Player.STATE_READY) return
        val task = Runnable {
            scheduledPlaylistFill = null
            if (pendingPlaylist !== plan || player.playbackState != Player.STATE_READY) {
                return@Runnable
            }
            val chunk = plan.nextChunk() ?: run {
                pendingPlaylist = null
                pendingMediaFactory = null
                return@Runnable
            }
            try {
                val sources = chunk.items.map { makeMediaSource(factory, it) }
                if (chunk.prepend) player.addMediaSources(0, sources)
                else player.addMediaSources(sources)
            } catch (_: Exception) {
                // Never expose file metadata in errors. Current audio keeps playing
                // even if an optional later chunk could not be added.
                pendingPlaylist = null
                pendingMediaFactory = null
                return@Runnable
            }
            if (plan.isComplete) {
                pendingPlaylist = null
                pendingMediaFactory = null
                applyStableShuffleIfComplete()
            } else stagePlaylistAfterReady()
        }
        scheduledPlaylistFill = task
        cacheHandler.postDelayed(task, 35L)
    }

    private fun makeMediaSource(
        factory: ProgressiveMediaSource.Factory,
        item: QueueTrack,
    ): MediaSource = factory.createMediaSource(
        MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(WebDavPlaybackTarget.url(item.encodedPath))
            .setCustomCacheKey(TransientCachePolicy.key(item))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
            .build(),
    )

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
            // Pause does not stop either full-file download.
            val selectedId = pending.mediaId
            temporaryCache?.prefetchCurrent(
                pending.key, pending.uri, pending.size, pending.upstream,
                onComplete = {
                    if (cacheEnabledForQueue &&
                        player.currentMediaItem?.mediaId == selectedId) {
                        fullyCachedCurrentId = selectedId
                        refreshNextPrefetch()
                    }
                },
            )
        }
        scheduledWriter = task
        cacheHandler.postDelayed(task, CacheStartPolicy.START_DELAY_MS)
    }

    private val cacheTransitionListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // MediaSession controls bypass service.resume().
            if (playWhenReady && deferredPrepare && player.playbackState == Player.STATE_IDLE) {
                deferredPrepare = false
                player.prepare()
            }
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            clearScheduledWriter()
            pendingCache = null
            fullyCachedCurrentId = null
            scheduledNextId = null
            temporaryCache?.stopCurrent()
            if (!cacheEnabledForQueue) return
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

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (!restoringShuffle) {
                configuredShuffle = if (shuffleModeEnabled && expectedQueueSize > 0)
                    newShuffleOrder(expectedQueueSize) else null
                stableShuffleApplied = false
                applyStableShuffleIfComplete()
            }
            refreshNextPrefetch()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            refreshNextPrefetch()
        }

        override fun onTimelineChanged(
            timeline: androidx.media3.common.Timeline,
            reason: Int,
        ) {
            applyStableShuffleIfComplete()
            // With staged playlist fill the next item may appear after COMPLETE.
            if (fullyCachedCurrentId != null && scheduledNextId == null) {
                refreshNextPrefetch()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                scheduleAfterFirstBuffer()
                stagePlaylistAfterReady()
            } else if (state == Player.STATE_BUFFERING) {
                // A rebuffering stream takes priority over both background jobs.
                clearScheduledWriter()
                scheduledPlaylistFill?.let(cacheHandler::removeCallbacks)
                scheduledPlaylistFill = null
            }
        }
    }
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        // Streaming from WebDAV with the screen off: without a Wi-Fi lock the
        // system may power down Wi-Fi mid-track (ADR-0074, M6-0/2). Media3
        // holds the wake and Wi-Fi locks only while playing or buffering.
        // Requires android.permission.WAKE_LOCK in the app manifest.
        .setWakeMode(C.WAKE_MODE_NETWORK)
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
            addListener(handoffListener)
            addListener(cacheTransitionListener)
        }

    fun cacheSnapshot(): TransientCacheStatus =
        temporaryCache?.snapshot() ?: TransientCacheStatus(CachePhase.IDLE, 0L, 0L)

    fun nextCacheSnapshot(): TransientCacheStatus =
        temporaryCache?.nextSnapshot() ?: TransientCacheStatus(CachePhase.IDLE, 0L, 0L)

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
        useTemporaryCache: Boolean,
    ) {
        clearScheduledWriter()
        cancelPlaylistFill()
        handoffTracker.startNewQueue()
        lastEndRecoveryId = null
        pendingCache = null
        fullyCachedCurrentId = null
        scheduledNextId = null
        temporaryCache?.stopCurrent()
        cacheEnabledForQueue = useTemporaryCache

        val t0 = SystemClock.elapsedRealtime()
        val queue = PlaybackQueue.create(tracks, selectedId)
        val t1 = SystemClock.elapsedRealtime()
        val factory = WebDavMedia3DataSource.Factory(email, appPassword, queue.items)
        val sourceFactory = if (useTemporaryCache) {
            val diskCache = temporaryCache ?: TransientMediaCache(applicationContext)
                .also { temporaryCache = it }
            diskCache.forPlayback(factory)
        } else factory
        val mediaFactory = ProgressiveMediaSource.Factory(sourceFactory)
        val t2 = SystemClock.elapsedRealtime()

        activeRoutes = if (useTemporaryCache) queue.items.associateBy { it.id }
            else emptyMap()
        upstreamFactory = if (useTemporaryCache) factory else null
        expectedQueueSize = queue.size
        mediaIdIndices = queue.items.mapIndexed { index, item -> item.id to index }.toMap()
        val plan = StagedPlaylistPlan.create(queue)
        // Only five sources at most are materialized before first sound.
        val firstSources = plan.seed.map { makeMediaSource(mediaFactory, it) }
        val t3 = SystemClock.elapsedRealtime()

        pendingPlaylist = if (plan.isComplete) null else plan
        pendingMediaFactory = if (plan.isComplete) null else mediaFactory
        player.setMediaSources(firstSources, plan.seedSelectedIndex, C.TIME_UNSET)
        val t4 = SystemClock.elapsedRealtime()
        player.prepare()
        player.play()
        val t5 = SystemClock.elapsedRealtime()
        latestSetup = PlaybackSetupTimings(
            queueMs = t1 - t0,
            routesAndCacheMs = t2 - t1,
            seedSourcesMs = t3 - t2,
            setSourcesMs = t4 - t3,
            prepareAndPlayMs = t5 - t4,
        )
    }

    /** Explicit Next/Previous means play the selected item, even after ENDED. */
    fun resumeAfterManualSkip() {
        if (player.playbackState == Player.STATE_ENDED ||
            player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    fun pause() = player.pause()

    fun resume() = player.play()

    fun stop() {
        handoffTracker.stop()
        lastEndRecoveryId = null
        clearScheduledWriter()
        fullyCachedCurrentId = null
        scheduledNextId = null
        cancelPlaylistFill()
        expectedQueueSize = 0
        mediaIdIndices = emptyMap()
        pendingCache = null
        temporaryCache?.stopCurrent()
        cacheEnabledForQueue = false
        player.stop()
        player.clearMediaItems()
        activeRoutes = emptyMap()
        upstreamFactory = null
    }

    override fun close() {
        stop()
        player.removeListener(handoffListener)
        player.removeListener(cacheTransitionListener)
        player.release()
        temporaryCache?.close()
    }
}
