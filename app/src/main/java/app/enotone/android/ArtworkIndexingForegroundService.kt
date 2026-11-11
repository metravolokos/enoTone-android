package app.enotone.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal enum class ArtworkScanState { IDLE, RUNNING, PAUSED, STOPPED, COMPLETE, ERROR }

internal data class ArtworkScanStatus(
    val state: ArtworkScanState = ArtworkScanState.IDLE,
    val completed: Int = 0,
    val total: Int = 0,
    val terminating: Boolean = false,
)

/**
 * Credentials are handed directly in RAM. No password in an Intent,
 * PendingIntent, notification, Room, artwork, preferences, or log.
 */
internal object ArtworkScanRuntime {
    val status = MutableStateFlow(ArtworkScanStatus())
    private var pending: StoredWebDavLogin? = null

    @Synchronized fun prepare(email: String, password: String): Boolean {
        if (email.isBlank() || password.isBlank() ||
            status.value.state == ArtworkScanState.RUNNING ||
            status.value.terminating) return false
        pending = StoredWebDavLogin(email, password)
        status.value = ArtworkScanStatus(state = ArtworkScanState.RUNNING)
        return true
    }

    @Synchronized fun claim(): StoredWebDavLogin? =
        pending.also { pending = null }

    @Synchronized fun update(state: ArtworkScanState, completed: Int, total: Int,
        terminating: Boolean = false) {
        if (state == ArtworkScanState.RUNNING &&
            status.value.state != ArtworkScanState.RUNNING) return
        if (state != ArtworkScanState.RUNNING) pending = null
        status.value = ArtworkScanStatus(state, completed, total, terminating)
    }
}

/**
 * Optional automatic artwork indexing, resumed explicitly on EACH app launch
 * in AUTO_AFTER_IMPORT mode. Uses one source per real album, falling back only
 * if its artwork is deterministically missing. Source and album checkpoints
 * live in permanent filesDir and survive process death/restart.
 *
 * Never starts on device reboot or from WorkManager. Audio and tag indexing
 * remain higher priority; a bounded foreground session has a 4-hour cap.
 */
class ArtworkIndexingForegroundService : Service() {
    companion object {
        const val ACTION_START = "app.enotone.android.artwork.START"
        const val ACTION_PAUSE = "app.enotone.android.artwork.PAUSE"
        const val ACTION_STOP = "app.enotone.android.artwork.STOP"
        private const val CHANNEL = "enotone_artwork"
        private const val NOTIFICATION_ID = 4057
        private const val MAX_SESSION_NANOS = 4L * 60 * 60 * 1_000_000_000
        private const val BETWEEN_ALBUM_MS = 200L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var indexJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_PAUSE -> stopSession(ArtworkScanState.PAUSED)
            ACTION_STOP -> stopSession(ArtworkScanState.STOPPED)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startSession() {
        if (indexJob != null) return
        val login = ArtworkScanRuntime.claim()
        if (login == null) {
            stopSelf()
            return
        }
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL,
                    getString(R.string.artwork_index_title), NotificationManager.IMPORTANCE_LOW),
            )
            val initial = notification(0, 0)
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, initial,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else startForeground(NOTIFICATION_ID, initial)
        } catch (_: RuntimeException) {
            ArtworkScanRuntime.update(ArtworkScanState.ERROR, 0, 0, terminating = true)
            stopSelf()
            return
        }

        indexJob = scope.launch(Dispatchers.IO) {
            var completed = 0
            var total = 0
            try {
                val groups = ArtworkIndexPlanner.fromStoredLibrary(
                    applicationContext, login.email)
                val repository = ArtworkPreviewRepository(applicationContext)
                total = groups.size
                    val startedAt = System.nanoTime()
                for (group in groups) {
                        coroutineContext.ensureActive()
                        if (System.nanoTime() - startedAt >= MAX_SESSION_NANOS) break
                        // Tag import has priority; it has its own four-reader
                        // network limits and must never compete for bandwidth.
                        while (ManagedScanRuntime.status.value.state ==
                            ManagedScanState.RUNNING) {
                            delay(1200L)
                        }
                        if (!repository.isAlbumIndexed(login.email, group)) {
                            repository.albumCover(login.email, login.password,
                                group, requestedSide = 192)
                            delay(BETWEEN_ALBUM_MS)
                        }
                        if (repository.isAlbumIndexed(login.email, group)) completed++
                        ArtworkScanRuntime.update(ArtworkScanState.RUNNING, completed, total)
                        // Progress notifications contain COUNTS only.
                        val manager = getSystemService(NotificationManager::class.java)
                        if (completed % 5 == 0 || completed == total) {
                            try {
                                manager?.notify(NOTIFICATION_ID,
                                    notification(completed, total))
                            } catch (_: RuntimeException) {
                                // No reporting failure should stop indexing.
                            }
                        }
                    }
                    ArtworkScanRuntime.update(
                        if (completed == total) ArtworkScanState.COMPLETE
                        else ArtworkScanState.PAUSED, completed, total,
                        terminating = true)
            } catch (_: CancellationException) {
                // A user pause/stop already published its state.
            } catch (_: Exception) {
                ArtworkScanRuntime.update(ArtworkScanState.ERROR, completed, total,
                    terminating = true)
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun stopSession(state: ArtworkScanState) {
        val last = ArtworkScanRuntime.status.value
        ArtworkScanRuntime.update(state, last.completed, last.total,
            terminating = indexJob != null)
        indexJob?.cancel()
        if (indexJob == null) stopSelf()
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, ArtworkIndexingForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notification(completed: Int, total: Int): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.artwork_index_title))
            .setContentText(getString(R.string.artwork_index_progress, completed, total))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause,
                getString(R.string.managed_index_pause), actionIntent(ACTION_PAUSE, 11))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.managed_index_stop), actionIntent(ACTION_STOP, 12))
            .build()

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSession(ArtworkScanState.PAUSED)
        stopSelf()
    }

    override fun onDestroy() {
        val last = ArtworkScanRuntime.status.value
        if (last.state == ArtworkScanState.RUNNING) {
            ArtworkScanRuntime.update(ArtworkScanState.PAUSED,
                last.completed, last.total)
        } else if (last.terminating) {
            ArtworkScanRuntime.update(last.state,
                last.completed, last.total, terminating = false)
        }
        indexJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
