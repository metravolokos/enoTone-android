package app.enotone.playback

import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Media3 owns the playlist. Only hashed IDs and numeric state are checkpointed.
 * Queue layout and small position writes run on one serialized I/O executor.
 */
class EnoTonePlaybackService : MediaSessionService() {
    private lateinit var playback: PreviewAudioPlayback
    private lateinit var session: MediaSession
    private val localBinder = LocalBinder()
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { QueueStateFileStore(applicationContext) }
    private val positionThrottle = QueueWriteThrottle(QueueMonotonicClock {
        SystemClock.elapsedRealtime()
    })
    private var activeRevision: String? = null
    private var layoutWritePending = false
    private var layoutGeneration = 0L
    private var queueMutation = false
    private var pendingPositionSave: Runnable? = null

    private val periodicSave = object : Runnable {
        override fun run() {
            if (playback.player.isPlaying) savePosition()
            handler.postDelayed(this, 12_000L)
        }
    }

    private val persistenceListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!queueMutation && mediaItem != null) savePosition(urgent = true)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!queueMutation && !playWhenReady) savePosition()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            if (!queueMutation) savePosition(urgent = true)
        }

        // Аудит п. 4: перемотка (с экрана, из уведомления или гарнитуры) сохраняется и на паузе;
        // периодическое сохранение работает только во время воспроизведения.
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (!queueMutation && PlaybackSeekPolicy.isUserSeek(reason)) savePosition(urgent = true)
        }
    }

    /** Snapshot only the small current-item fields on main; never scan 3590 IDs. */
    private fun savePosition(urgent: Boolean = false) {
        if (layoutWritePending) return
        val revision = activeRevision ?: return
        val snapshot = playback.snapshotPosition(revision, System.currentTimeMillis()) ?: return
        val delay = positionThrottle.delayMs(urgent)
        pendingPositionSave?.let(handler::removeCallbacks)
        pendingPositionSave = null
        if (delay > 0L) {
            val task = Runnable { pendingPositionSave = null; savePosition() }
            pendingPositionSave = task
            handler.postDelayed(task, delay)
        } else {
            positionThrottle.markWritten()
            io.execute {
                try { store.writePosition(snapshot) } catch (_: Exception) {
                    // Never log file contents or private source details.
                }
            }
        }
    }

    /**
     * New queue/shuffle: change revision before any position event, then
     * serialize layout BEFORE the small checkpoint on the same I/O executor.
     */
    private fun saveLayoutAndPosition() {
        val nextRevision = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val layout = playback.snapshotLayout(nextRevision, now) ?: return
        val position = playback.snapshotPosition(nextRevision, now) ?: return
        val generation = ++layoutGeneration
        layoutWritePending = true
        pendingPositionSave?.let(handler::removeCallbacks)
        pendingPositionSave = null
        io.execute {
            val completed = runCatching {
                store.writeLayoutAndPosition(layout, position)
            }.isSuccess
            handler.post {
                if (generation == layoutGeneration) {
                    // The current revision advances only after both files exist.
                    if (completed) activeRevision = nextRevision
                    layoutWritePending = false
                    if (completed) savePosition(urgent = true)
                }
            }
        }
    }

    /** Must be called from Dispatchers.IO; never open a network connection. */
    fun readSavedQueue(): PersistedQueueState? = store.read()

    /** The caller already checked the opted-in vault, Room and account source. */
    fun restoreQueue(
        email: String,
        password: String,
        tracks: List<QueueTrack>,
        state: PersistedQueueState,
        useTemporaryCache: Boolean,
        filteredMissingTracks: Boolean,
    ): Boolean {
        if (playback.logicalQueueSize > 0 || tracks.isEmpty() ||
            tracks.map { it.id } != state.trackIds) return false
        queueMutation = true
        try {
            playback.restoreQueue(email, password, tracks, state, useTemporaryCache)
        } finally {
            queueMutation = false
        }
        if (filteredMissingTracks) saveLayoutAndPosition()
        else {
            activeRevision = state.queueRevision
            savePosition(urgent = true)
        }
        return true
    }

    inner class LocalBinder : Binder() {
        fun service(): EnoTonePlaybackService = this@EnoTonePlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        playback = PreviewAudioPlayback(this)
        // The callback fires after the player's full shuffle permutation exists.
        // Listener registration order must never determine persistence correctness.
        playback.onShuffleLayoutReady = { if (!queueMutation) saveLayoutAndPosition() }
        playback.player.addListener(persistenceListener)
        handler.postDelayed(periodicSave, 12_000L)
        val builder = MediaSession.Builder(this, playback.player)
        // Без session activity нажатие на уведомление ничего не открывает (дефект Huawei 2026-11-13).
        PlayerLaunchIntent.pendingIntent(this)?.let(builder::setSessionActivity)
        session = builder.build()
        addSession(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_LOCAL_BIND) localBinder else super.onBind(intent)

    val player: Player get() = playback.player

    fun playQueue(
        email: String,
        password: String,
        tracks: List<QueueTrack>,
        selectedId: String,
        useTemporaryCache: Boolean,
        sourceKey: String,
    ) {
        queueMutation = true
        try {
            playback.playQueue(email, password, tracks, selectedId, useTemporaryCache, sourceKey)
        } finally {
            queueMutation = false
        }
        saveLayoutAndPosition()
    }

    /** MediaSession next/previous continues to use native timeline navigation. */
    fun insertNext(tracks: List<QueueTrack>): QueueEditResult =
        editQueue(tracks, QueueEdit.PlayNext(tracks.map { it.id }))

    fun append(tracks: List<QueueTrack>): QueueEditResult =
        editQueue(tracks, QueueEdit.Append(tracks.map { it.id }))

    private fun editQueue(tracks: List<QueueTrack>, edit: QueueEdit): QueueEditResult {
        if (playback.logicalQueueSize == 0) {
            // No credentials are retained after Stop; the ViewModel starts a
            // fresh queue with its verified in-memory credentials.
            return QueueEditResult(QueueEditStatus.UNAVAILABLE)
        }
        val result: QueueEditResult
        queueMutation = true
        try {
            result = playback.editQueue(tracks, edit)
        } finally {
            queueMutation = false
        }
        if (result.count > 0) saveLayoutAndPosition()
        return result
    }

    val hasNext: Boolean get() = playback.player.hasNextMediaItem()
    val hasPrevious: Boolean get() = playback.player.hasPreviousMediaItem()
    val queueSize: Int get() = playback.logicalQueueSize
    val queuePosition: Int get() = playback.logicalQueuePosition
    val loadedQueueSize: Int get() = playback.loadedQueueSize
    val displayDurationMs: Long get() = playback.displayDurationMs
    val activeSourceKey: String? get() = playback.activeSourceKey
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

    fun cycleRepeatMode() {
        playback.player.repeatMode = PlaybackModes.nextRepeat(playback.player.repeatMode)
    }

    fun toggleShuffle() {
        playback.player.shuffleModeEnabled = !playback.player.shuffleModeEnabled
    }

    val canSeekCurrent: Boolean
        get() = playback.player.isCommandAvailable(
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
            PlaybackSeekPolicy.isKnownDuration(playback.player.duration)

    fun seekToFraction(fraction: Float): Boolean {
        val player = playback.player
        if (!player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return false
        val target = PlaybackSeekPolicy.positionForFraction(player.duration, fraction)
            ?: return false
        player.seekTo(target)
        return true
    }

    fun pause() = playback.pause()
    fun resume() = playback.resume()

    /** Explicit Stop forgets both files. Android Settings 'Force stop' does not. */
    fun stopPlayback() {
        pendingPositionSave?.let(handler::removeCallbacks)
        pendingPositionSave = null
        activeRevision = null
        layoutGeneration++
        layoutWritePending = false
        queueMutation = true
        try { playback.stop() } finally { queueMutation = false }
        io.execute {
            try { store.clear() } catch (_: Exception) { }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition(urgent = true)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(periodicSave)
        pendingPositionSave?.let(handler::removeCallbacks)
        pendingPositionSave = null
        savePosition(urgent = true)
        playback.player.removeListener(persistenceListener)
        playback.onShuffleLayoutReady = null
        session.release()
        playback.close()
        io.shutdown() // Ordered flush; no blocking main-thread fsync.
        super.onDestroy()
    }

    companion object {
        const val ACTION_LOCAL_BIND = "app.enotone.android.action.LOCAL_PLAYBACK_BIND"
    }
}
