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
    /** Only the synchronous main-thread seed setup during a restored launch. */
    val restoreSeedMs: Long? = null,
)

@OptIn(UnstableApi::class)
class PreviewAudioPlayback(
    context: Context,
    private val dataSourceFactoryOverride: DataSource.Factory? = null,
) : AutoCloseable {
    // Lazy: in baseline mode neither initialize nor touch the disk cache.
    private val applicationContext = context.applicationContext

    @Volatile
    private var temporaryCache: TransientMediaCache? = null

    /** Причина, по которой кэш недоступен; только код, без путей и текстов. */
    @Volatile
    private var transientCacheFailure: TransientCacheFailure? = null

    private var cachePreparing = false

    /** Служба закрывается: кэш, открытый после закрытия, использовать нельзя. */
    @Volatile
    private var closed = false

    private var cacheEnabledForQueue = false
    private var activeRoutes: Map<String, QueueTrack> = emptyMap()
    private var upstreamFactory: DataSource.Factory? = null
    private var routesFactory: WebDavMedia3DataSource.Factory? = null
    private var mediaSourceFactory: ProgressiveMediaSource.Factory? = null
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
        override fun onEvents(player: Player, events: Player.Events) {
            // Capture duration on the Media3 event itself, not only when the UI
            // happens to poll. A later transient TIME_UNSET must not erase it.
            durationMemory.resolve(player.currentMediaItem?.mediaId, player.duration)
        }
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
        if (alreadyRecovered(endedId) || !canRecoverEnded()) return
        cacheHandler.post {
            if (player.currentMediaItem?.mediaId != endedId ||
                alreadyRecovered(endedId) || !canRecoverEnded()) return@post
            lastEndRecoveryId = endedId
            PlaybackContinuationPolicy.recoverUnexpectedEnd(player)
        }
    }

    /**
     * Одна попытка на трек защищает от петли «следующий → ENDED». При повторе одного трека
     * тот же id законно заканчивается на каждом круге — иначе повтор оборвался бы после второго
     * проигрывания (ревью #132). Битый файл даёт ERROR, а не ENDED, поэтому петли нет.
     */
    private fun alreadyRecovered(endedId: String): Boolean =
        player.repeatMode != Player.REPEAT_MODE_ONE && lastEndRecoveryId == endedId

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
    internal val queueNavigation = PlaybackQueueNavigation()
    private var sourceKeyForQueue: String? = null
    val activeSourceKey: String? get() = sourceKeyForQueue
    private var configuredShuffle: IntArray? = null
    private var stableShuffleApplied = false
    /** Called only after configuredShuffle is ready, including MediaSession controls. */
    var onShuffleLayoutReady: (() -> Unit)? = null
    private val durationMemory = PlaybackDurationMemory()
    private var restoringShuffle = false
    internal var deferredPrepare = false
    private var stageBeforePrepare = false

    private fun newShuffleOrder(size: Int, firstIndex: Int): IntArray =
        QueueShuffleOrder.create(size, firstIndex, System.nanoTime())

    /** Staged Media3 order is incomplete; install an exact full permutation once. */
    private fun applyStableShuffleIfComplete() {
        if (stableShuffleApplied || !player.shuffleModeEnabled ||
            player.mediaItemCount != expectedQueueSize) return
        val indices = configuredShuffle ?: return
        stableShuffleApplied = true
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(indices.copyOf(), System.nanoTime()))
    }

    /** Constant-time checkpoint; large list validation happens on I/O executor. */
    fun snapshotPosition(revision: String, savedAtEpochMs: Long): PersistedPositionState? {
        val current = player.currentMediaItem?.mediaId ?: return null
        if (sourceKeyForQueue == null) return null
        return PersistedPositionState(
            selectedTrackId = current,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            repeatMode = player.repeatMode,
            shuffleEnabled = player.shuffleModeEnabled,
            savedAtEpochMs = savedAtEpochMs,
            queueRevision = revision,
            durationMs = displayDurationMs,
        )
    }

    /**
     * Duration for the UI and seek commands: the real Media3 value when known;
     * otherwise the last known value for this same item. A restored checkpoint
     * seeds that value before the first Play. A transient TIME_UNSET must not
     * erase a duration Media3 has already reported for the current track.
     */
    val displayDurationMs: Long get() = durationMemory.resolve(
        mediaId = player.currentMediaItem?.mediaId,
        rawDurationMs = player.duration,
    )

    /** Immutable original queue and full permutation; only on queue/shuffle changes. */
    fun snapshotLayout(revision: String, savedAtEpochMs: Long): PersistedQueueLayout? {
        val sourceKey = sourceKeyForQueue ?: return null
        if (originalTrackIds.isEmpty()) return null
        return PersistedQueueLayout(
            trackIds = originalTrackIds,
            shuffledIndices = if (player.shuffleModeEnabled) configuredShuffle?.copyOf() else null,
            shuffleEnabled = player.shuffleModeEnabled,
            sourceKey = sourceKey,
            queueRevision = revision,
            savedAtEpochMs = savedAtEpochMs,
        )
    }
    private var latestSetup = PlaybackSetupTimings(0L, 0L, 0L, 0L, 0L)

    val logicalQueueSize: Int get() = expectedQueueSize
    val currentTrackFullyCached: Boolean
        get() = player.currentMediaItem?.mediaId?.let { it == fullyCachedCurrentId } == true
    val logicalQueuePosition: Int get() {
        val id = player.currentMediaItem?.mediaId ?: return 0
        return queueNavigation.positionOf(id) ?: 0
    }

    /** Reuses the cached immutable order so position polling never scans a long queue. */
    fun queueSnapshot(): PlaybackQueueSnapshot? =
        queueNavigation.snapshot(player.currentMediaItem?.mediaId)

    /** Select by visible playback-order position without blocking on staged sources. */
    fun skipToQueuePosition(position: Int): Boolean = queueNavigation.selectPosition(
        position = position,
        currentTrackId = player.currentMediaItem?.mediaId,
        seekLoadedTarget = ::seekLoadedQueueTarget,
        stageMoreItems = ::stageMoreQueueItems,
    )

    internal fun stageMoreQueueItems(): Boolean {
        if (pendingPlaylist == null) return false
        stagePlaylistAfterReady()
        return true
    }

    private fun seekLoadedQueueTarget(targetId: String): Boolean =
        queueTimelineIndex(targetId)?.let { timelineIndex ->
            player.seekToDefaultPosition(timelineIndex)
            resumeAfterManualSkip()
            true
        } ?: false

    internal fun queueTimelineIndex(targetId: String): Int? = mediaIdIndices[targetId]?.let { originalIndex ->
        val plan = pendingPlaylist
        val timelineIndex = when {
            plan != null -> plan.timelineIndex(originalIndex)
            player.mediaItemCount == expectedQueueSize -> originalIndex
            else -> null
        }
        timelineIndex?.takeIf {
            it in 0 until player.mediaItemCount && player.getMediaItemAt(it).mediaId == targetId
        }
    }

    val loadedQueueSize: Int get() =
        pendingPlaylist?.loadedCount ?: player.mediaItemCount
    fun setupTimings(): PlaybackSetupTimings = latestSetup

    private fun cancelPlaylistFill() {
        scheduledPlaylistFill?.let(cacheHandler::removeCallbacks)
        scheduledPlaylistFill = null
        pendingPlaylist = null
        pendingMediaFactory = null
        stageBeforePrepare = false
    }

    /** Fill one bounded chunk per UI turn; restore may stage while still IDLE. */
    private fun mayFillPlaylist(): Boolean =
        player.playbackState == Player.STATE_READY ||
            player.playbackState == Player.STATE_ENDED ||
            (queueNavigation.hasPendingSeek &&
                (player.playbackState == Player.STATE_IDLE ||
                    player.playbackState == Player.STATE_BUFFERING)) ||
            (stageBeforePrepare && player.playbackState == Player.STATE_IDLE)

    private fun stagePlaylistAfterReady() {
        val plan = pendingPlaylist ?: return
        val factory = pendingMediaFactory ?: return
        if (scheduledPlaylistFill != null || !mayFillPlaylist()) return
        val task = Runnable {
            scheduledPlaylistFill = null
            if (pendingPlaylist !== plan || !mayFillPlaylist()) {
                return@Runnable
            }
            val targetIndex = queueNavigation.pendingTargetId?.let(mediaIdIndices::get)
            val chunk = if (targetIndex == null) plan.nextChunk()
                else plan.nextChunk(targetIndex) ?: plan.nextChunk()
            if (chunk == null) {
                pendingPlaylist = null
                pendingMediaFactory = null
                return@Runnable
            }
            try {
                val sources = chunk.items.map { makeMediaSource(factory, it) }
                player.addMediaSources(chunk.insertionIndex, sources)
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
                stageBeforePrepare = false
                applyStableShuffleIfComplete()
            }
            queueNavigation.applyPendingSeek(::seekLoadedQueueTarget)
            if (!plan.isComplete) stagePlaylistAfterReady()
        }
        scheduledPlaylistFill = task
        cacheHandler.postDelayed(task, 35L)
    }

    /**
     * A user edit cannot run against a partial Media3 timeline: otherwise the
     * original stage would later append stale items or duplicate moved IDs.
     * Finish only local source construction (no WebDAV requests), then edit.
     */
    private fun finishPendingPlaylist() {
        val plan = pendingPlaylist ?: return
        val factory = pendingMediaFactory ?: return
        scheduledPlaylistFill?.let(cacheHandler::removeCallbacks)
        scheduledPlaylistFill = null
        if (plan.isContiguous) {
            val beforeSources = plan.items.subList(0, plan.loadedStartIndex)
                .map { makeMediaSource(factory, it) }
            val afterSources = plan.items.subList(plan.loadedEndExclusive, plan.items.size)
                .map { makeMediaSource(factory, it) }
            if (beforeSources.isNotEmpty()) player.addMediaSources(0, beforeSources)
            if (afterSources.isNotEmpty()) player.addMediaSources(afterSources)
        } else {
            while (!plan.isComplete) {
                val gap = plan.nextGap() ?: error("Missing staged queue gap")
                val sources = gap.items.map { makeMediaSource(factory, it) }
                player.addMediaSources(gap.insertionIndex, sources)
            }
        }
        pendingPlaylist = null
        pendingMediaFactory = null
        stageBeforePrepare = false
        applyStableShuffleIfComplete()
    }

    private fun makeMediaSource(
        factory: ProgressiveMediaSource.Factory,
        item: QueueTrack,
    ): MediaSource = checkNotNull(routesFactory).trackMediaSource(item.id,
        factory.createMediaSource(MediaItem.Builder()
            .setMediaId(item.id).setUri(WebDavPlaybackTarget.url(item.encodedPath))
            .setCustomCacheKey(TransientCachePolicy.key(item))
            .setMediaMetadata(QueueMetadata.of(item)).build()),
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
                val firstIndex = player.currentMediaItem?.mediaId?.let(mediaIdIndices::get) ?: 0
                configuredShuffle = if (shuffleModeEnabled && expectedQueueSize > 0)
                    newShuffleOrder(expectedQueueSize, firstIndex) else null
                queueNavigation.updateOrder(
                    originalTrackIds, mediaIdIndices, configuredShuffle, player.shuffleModeEnabled,
                )
                stableShuffleApplied = false
                applyStableShuffleIfComplete()
            }
            refreshNextPrefetch()
            if (!restoringShuffle && expectedQueueSize > 0) onShuffleLayoutReady?.invoke()
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
            } else if (state == Player.STATE_ENDED) {
                stagePlaylistAfterReady()
            } else if (state == Player.STATE_BUFFERING) {
                // A rebuffering stream takes priority over both background jobs.
                clearScheduledWriter()
                scheduledPlaylistFill?.let(cacheHandler::removeCallbacks)
                scheduledPlaylistFill = null
                if (queueNavigation.hasPendingSeek) stagePlaylistAfterReady()
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
    private val timelineEditor = PlaybackQueueTimelineEditor(player)

    fun cacheSnapshot(): TransientCacheStatus =
        temporaryCache?.snapshot() ?: TransientCacheStatus(CachePhase.IDLE, 0L, 0L)

    fun nextCacheSnapshot(): TransientCacheStatus =
        temporaryCache?.nextSnapshot() ?: TransientCacheStatus(CachePhase.IDLE, 0L, 0L)

    /** Причина недоступности кэша для диагностики; null — кэш доступен или ещё готовится. */
    fun transientCacheFailure(): TransientCacheFailure? = transientCacheFailure

    /**
     * Включён ли кэш для собранной очереди. Решение принимается один раз при
     * сборке (`CacheModeDecision`) и не пересматривается, даже если кэш появится
     * позже — иначе предзагрузка работала бы без маршрутов и настроек источника.
     */
    fun cacheEnabledForCurrentQueue(): Boolean = cacheEnabledForQueue

    /**
     * Готовит кэш вне главного потока. Удаление каталога до 512 МиБ и открытие
     * `SimpleCache` с SQLite — тяжёлые операции: на UI-потоке они давали фриз
     * и ANR при холодном старте воспроизведения.
     *
     * Сбой не фатален: очередь играет через сеть, причина остаётся кодом в
     * диагностике. Повторные вызовы не пересоздают кэш.
     */
    fun prepareTransientCache() {
        if (temporaryCache != null || cachePreparing) return
        cachePreparing = true
        Thread({
            val decision = createTransientCacheSafely(applicationContext)
            cacheHandler.post {
                cachePreparing = false
                when (decision) {
                    is TransientCacheFactory.Decision.UseCache -> {
                        // Служба могла закрыться, пока кэш готовился.
                        if (closed) decision.cache.close() else temporaryCache = decision.cache
                    }
                    is TransientCacheFactory.Decision.PlayWithoutCache ->
                        transientCacheFailure = decision.reason
                }
            }
        }, "enotone-cache-open").apply { isDaemon = true }.start()
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
        useTemporaryCache: Boolean,
        sourceKey: String,
    ) {
        deferredPrepare = false
        stageBeforePrepare = false
        queueNavigation.clearPendingSeek()
        sourceKeyForQueue = sourceKey
        clearScheduledWriter()
        cancelPlaylistFill()
        handoffTracker.startNewQueue()
        lastEndRecoveryId = null
        pendingCache = null
        fullyCachedCurrentId = null
        scheduledNextId = null
        temporaryCache?.stopCurrent()

        val t0 = SystemClock.elapsedRealtime()
        durationMemory.clear()
        val queue = PlaybackQueue.create(tracks, selectedId)
        val t1 = SystemClock.elapsedRealtime()
        val factory = WebDavMedia3DataSource.Factory(email, appPassword, queue.items, dataSourceFactoryOverride)
        // Кэш не создаётся здесь: его готовит prepareTransientCache() вне главного
        // потока. Если он не готов или недоступен — играем через сеть, а не падаем.
        val diskCache = temporaryCache
        // Режим кэша решается ОДИН раз рядом с готовностью кэша: очередь,
        // собранная без кэша, не включает предзагрузку, даже если кэш появится позже.
        cacheEnabledForQueue = CacheModeDecision.cacheEnabledForQueue(
            requested = useTemporaryCache, cacheAvailable = diskCache != null)
        val sourceFactory = if (cacheEnabledForQueue && diskCache != null) {
            diskCache.forPlayback(factory)
        } else factory
        if (useTemporaryCache && diskCache == null && transientCacheFailure == null) {
            // Первый запуск: подготовку могли не успеть начать — запускаем сейчас.
            prepareTransientCache()
        }
        val mediaFactory = ProgressiveMediaSource.Factory(sourceFactory)
        routesFactory = factory
        mediaSourceFactory = mediaFactory
        val t2 = SystemClock.elapsedRealtime()

        activeRoutes = if (cacheEnabledForQueue) queue.items.associateBy { it.id }
            else emptyMap()
        upstreamFactory = if (cacheEnabledForQueue) factory else null
        expectedQueueSize = queue.size
        mediaIdIndices = queue.items.mapIndexed { index, item -> item.id to index }.toMap()
        originalTrackIds = queue.items.map { it.id }
        configuredShuffle = if (player.shuffleModeEnabled) newShuffleOrder(queue.size, queue.position) else null
        queueNavigation.updateOrder(originalTrackIds, mediaIdIndices, configuredShuffle, player.shuffleModeEnabled)
        stableShuffleApplied = false
        val plan = StagedPlaylistPlan.create(queue)
        // Only five sources at most are materialized before first sound.
        val firstSources = plan.seed.map { makeMediaSource(mediaFactory, it) }
        val t3 = SystemClock.elapsedRealtime()

        pendingPlaylist = if (plan.isComplete) null else plan
        pendingMediaFactory = if (plan.isComplete) null else mediaFactory
        player.setMediaSources(firstSources, plan.seedSelectedIndex, C.TIME_UNSET)
        val t4 = SystemClock.elapsedRealtime()
        applyStableShuffleIfComplete()
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

    /**
     * Mutate the existing ExoPlayer timeline, preserving the current MediaPeriod,
     * position, playWhenReady, repeat mode and stable shuffle traversal.
     * The caller owns the persistence transaction.
     */
    fun editQueue(tracks: List<QueueTrack>, edit: QueueEdit): QueueEditResult {
        val currentId = player.currentMediaItem?.mediaId
        return if (currentId == null || originalTrackIds.isEmpty()) {
            QueueEditResult(QueueEditStatus.UNAVAILABLE)
        } else {
            val plan = QueueEditPlanner.plan(
                QueueLayout(originalTrackIds,
                    if (player.shuffleModeEnabled) configuredShuffle else null, currentId),
                edit,
            )
            if (plan.movedIds.isEmpty()) plan.result
            else applyQueueEdit(tracks, edit, plan)
        }
    }

    private fun applyQueueEdit(
        tracks: List<QueueTrack>,
        edit: QueueEdit,
        plan: QueueEditPlan,
    ): QueueEditResult {
        val byId = tracks.associateBy { it.id }
        val knownIds = originalTrackIds.toHashSet()
        val newIds = plan.layout.trackIds.filterNot { it in knownIds }
        val newTracks = newIds.map { byId.getValue(it) }
        val factory = checkNotNull(mediaSourceFactory)
        val route = checkNotNull(routesFactory)
        // Register routes before creating local sources. A source retains its
        // route while Media3 owns a MediaPeriod for it.
        route.addTracks(newTracks)
        val sources = newTracks.associate { it.id to makeMediaSource(factory, it) }
        finishPendingPlaylist()
        require(player.mediaItemCount == originalTrackIds.size) { "Incomplete playback timeline" }
        val working = originalTrackIds.toMutableList()
        stableShuffleApplied = true
        expectedQueueSize = plan.layout.trackIds.size
        configuredShuffle = plan.layout.shuffledIndices?.copyOf()
        if (plan.layout.shuffleEnabled) timelineEditor.applyShuffledEdit(plan, newIds, sources, working)
        else timelineEditor.applyLinearEdit(plan, edit, sources, working)

        check(working == plan.layout.trackIds) { "Queue edit order mismatch" }
        route.setActiveTrackIds(plan.layout.trackIds)
        originalTrackIds = plan.layout.trackIds
        mediaIdIndices = originalTrackIds.withIndex().associate { it.value to it.index }
        queueNavigation.clearPendingSeek()
        queueNavigation.updateOrder(originalTrackIds, mediaIdIndices, configuredShuffle, player.shuffleModeEnabled)
        activeRoutes = (activeRoutes - plan.removedIds.toSet()) + newTracks.associateBy { it.id }
        sourceKeyForQueue = QueueEditSource.edited(checkNotNull(sourceKeyForQueue))
        // Previous next-song prefetch is now stale even if current audio continues.
        temporaryCache?.cancelNext()
        scheduledNextId = null
        refreshNextPrefetch()
        return plan.result
    }

    /**
     * Only a bounded seed is created synchronously. The remaining Media3
     * sources are appended/prepended in 32-item Handler turns even while IDLE;
     * prepare() and network GET are deferred until explicit Play.
     */
    fun restoreQueue(
        email: String,
        appPassword: String,
        tracks: List<QueueTrack>,
        state: PersistedQueueState,
        useTemporaryCache: Boolean,
    ) {
        val t0 = SystemClock.elapsedRealtime()
        require(state.trackIds == tracks.map { it.id })
        clearScheduledWriter()
        cancelPlaylistFill()
        handoffTracker.startNewQueue()
        lastEndRecoveryId = null
        pendingCache = null
        fullyCachedCurrentId = null
        scheduledNextId = null
        temporaryCache?.stopCurrent()

        val queue = PlaybackQueue.create(tracks, state.selectedTrackId)
        val t1 = SystemClock.elapsedRealtime()
        val factory = WebDavMedia3DataSource.Factory(email, appPassword, queue.items, dataSourceFactoryOverride)
        // Кэш не создаётся здесь: его готовит prepareTransientCache() вне главного
        // потока. Если он не готов или недоступен — играем через сеть, а не падаем.
        val diskCache = temporaryCache
        cacheEnabledForQueue = CacheModeDecision.cacheEnabledForQueue(
            requested = useTemporaryCache, cacheAvailable = diskCache != null)
        val sourceFactory = if (cacheEnabledForQueue && diskCache != null) {
            diskCache.forPlayback(factory)
        } else factory
        if (useTemporaryCache && diskCache == null && transientCacheFailure == null) {
            // Первый запуск: подготовку могли не успеть начать — запускаем сейчас.
            prepareTransientCache()
        }
        val mediaFactory = ProgressiveMediaSource.Factory(sourceFactory)
        routesFactory = factory
        mediaSourceFactory = mediaFactory
        val t2 = SystemClock.elapsedRealtime()
        activeRoutes = if (cacheEnabledForQueue) queue.items.associateBy { it.id } else emptyMap()
        upstreamFactory = if (cacheEnabledForQueue) factory else null
        expectedQueueSize = queue.size
        originalTrackIds = queue.items.map { it.id }
        sourceKeyForQueue = state.sourceKey
        mediaIdIndices = queue.items.mapIndexed { index, item -> item.id to index }.toMap()
        configuredShuffle = state.shuffledIndices?.copyOf()
        queueNavigation.clearPendingSeek()
        queueNavigation.updateOrder(originalTrackIds, mediaIdIndices, configuredShuffle, player.shuffleModeEnabled)
        stableShuffleApplied = false
        durationMemory.clear()
        durationMemory.remember(state.selectedTrackId, state.durationMs)

        val plan = StagedPlaylistPlan.create(queue)
        val seed = plan.seed.map { makeMediaSource(mediaFactory, it) }
        val t3 = SystemClock.elapsedRealtime()
        pendingPlaylist = if (plan.isComplete) null else plan
        pendingMediaFactory = if (plan.isComplete) null else mediaFactory
        // Suppress re-randomization: the saved full permutation is authoritative.
        restoringShuffle = true
        try {
            player.shuffleModeEnabled = false
            player.repeatMode = state.repeatMode
            player.setMediaSources(seed, plan.seedSelectedIndex, state.positionMs)
            player.shuffleModeEnabled = state.shuffleEnabled
            player.pause()
            deferredPrepare = true
            stageBeforePrepare = !plan.isComplete
        } finally {
            restoringShuffle = false
        }
        queueNavigation.updateOrder(originalTrackIds, mediaIdIndices, configuredShuffle, player.shuffleModeEnabled)
        applyStableShuffleIfComplete()
        val t4 = SystemClock.elapsedRealtime()
        latestSetup = PlaybackSetupTimings(
            queueMs = t1 - t0,
            routesAndCacheMs = t2 - t1,
            seedSourcesMs = t3 - t2,
            setSourcesMs = t4 - t3,
            prepareAndPlayMs = 0L,
            restoreSeedMs = t4 - t0,
        )
        if (stageBeforePrepare) stagePlaylistAfterReady()
    }

    /** Explicit Next/Previous means play the selected item, even after ENDED. */
    fun resumeAfterManualSkip() {
        if (player.playbackState == Player.STATE_ENDED ||
            player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    fun pause() = player.pause()

    /** Retry the errored media item in place without touching queue, routes or cache. */
    fun retryCurrent(): Boolean = PlaybackErrorRetry.retryCurrent(player) {
        deferredPrepare = false
    }

    fun resume() {
        // Ошибка оставляет плеер в IDLE с сохранённым playerError: обычный play()
        // не готовит источник, и Play перестаёт работать до смены трека.
        if (player.playerError != null) {
            recoverAfterError()
            return
        }
        if (deferredPrepare && player.playbackState == Player.STATE_IDLE) {
            deferredPrepare = false
            player.prepare()
        }
        player.play()
    }

    fun stop() {
        handoffTracker.stop()
        lastEndRecoveryId = null
        clearScheduledWriter()
        fullyCachedCurrentId = null
        scheduledNextId = null
        cancelPlaylistFill()
        expectedQueueSize = 0
        mediaIdIndices = emptyMap()
        originalTrackIds = emptyList()
        queueNavigation.clear()
        sourceKeyForQueue = null
        configuredShuffle = null
        stableShuffleApplied = false
        deferredPrepare = false
        pendingCache = null
        // Release so sign-out can delete the directory and the Media3 index safely.
        temporaryCache?.close()
        temporaryCache = null
        cacheEnabledForQueue = false
        player.stop()
        player.clearMediaItems()
        // stop()/clearMediaItems() emit events; clear after them so the old
        // current item cannot seed the memory again during teardown.
        durationMemory.clear()
        activeRoutes = emptyMap()
        upstreamFactory = null
        routesFactory = null
        mediaSourceFactory = null
    }

    override fun close() {
        closed = true
        stop()
        player.removeListener(handoffListener)
        player.removeListener(cacheTransitionListener)
        player.release()
        temporaryCache?.close()
    }
}
