package app.enotone.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.IBinder
import app.enotone.library.ManagedIndexProgress
import app.enotone.library.ManagedTagIndexSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow

internal enum class ManagedScanState { IDLE, RUNNING, PAUSED, STOPPED, COMPLETE, ERROR }

internal data class ManagedScanStatus(
    val state: ManagedScanState = ManagedScanState.IDLE,
    val progress: ManagedIndexProgress? = null,
    val terminating: Boolean = false,
    // Fixed codes only: never a Throwable message, URL, username or file name.
    val failureStage: String? = null,
    val failureReason: String? = null,
)

/**
 * The password never enters an Intent, PendingIntent, log or notification.
 * Background execution survives Activity.onStop(), not process death.
 * Completed tag checkpoints are independently durable in Room.
 */
internal object ManagedScanRuntime {
    val status = MutableStateFlow(ManagedScanStatus())
    private var pending: StoredWebDavLogin? = null

    @Synchronized fun prepare(email: String, password: String): Boolean {
        require(email.isNotBlank() && password.isNotBlank())
        if (status.value.state == ManagedScanState.RUNNING || status.value.terminating) return false
        pending = StoredWebDavLogin(email, password)
        status.value = ManagedScanStatus(ManagedScanState.RUNNING)
        return true
    }

    @Synchronized fun claim(): StoredWebDavLogin? =
        pending.also { pending = null }

    @Synchronized fun update(
        state: ManagedScanState,
        progress: ManagedIndexProgress? = null,
        terminating: Boolean = false,
    ) {
        // A delayed progress callback must never undo a user's Pause/Stop.
        if (state == ManagedScanState.RUNNING &&
            (status.value.state != ManagedScanState.RUNNING || status.value.terminating)) return
        if (state != ManagedScanState.RUNNING) pending = null
        val last = status.value
        val terminalCode = when (state) {
            ManagedScanState.PAUSED -> "PAUSED"
            ManagedScanState.STOPPED -> "STOPPED"
            ManagedScanState.ERROR -> "FAILED_INTERNAL"
            else -> null
        }
        val finalProgress = progress ?: last.progress?.let {
            if (terminalCode == null) it else it.copy(stopCode = terminalCode)
        }
        status.value = ManagedScanStatus(
            state, finalProgress, terminating,
            failureStage = if (state == last.state) last.failureStage else null,
            failureReason = if (state == last.state) last.failureReason else null,
        )
    }

    @Synchronized fun fail(stage: String, cause: Exception) {
        // Classify only exception types, NEVER exception messages/stack traces
        // that might contain private remote paths or authorization details.
        val reason = when (cause) {
            is SQLiteException -> "SQLITE"
            is SecurityException -> "SECURITY"
            is java.io.IOException -> "IO"
            is IllegalArgumentException -> "ARGUMENT"
            is IllegalStateException -> "STATE"
            else -> "UNEXPECTED"
        }
        pending = null
        val last = status.value
        val stopped = last.progress?.copy(
            stopCode = "FAILED_INTERNAL",
            lastErrorStage = stage,
            lastErrorReason = reason,
            lastErrorHttpStatus = 0,
        )
        status.value = ManagedScanStatus(
            ManagedScanState.ERROR, stopped, terminating = true,
            failureStage = stage, failureReason = reason,
        )
    }
}

/**
 * A user-triggered, bounded dataSync foreground service. No background FGS
 * launch, boot receiver, WorkManager job quota, Wi-Fi filter or automatic restart.
 * Each batch retains the existing 60 GET / 4 MiB limits, and Room commits each
 * completed file. UI may be stopped or screen locked while this service runs.
 */
class IndexingForegroundService : Service() {
    companion object {
        const val ACTION_START = "app.enotone.android.index.START"
        const val ACTION_PAUSE = "app.enotone.android.index.PAUSE"
        const val ACTION_STOP = "app.enotone.android.index.STOP"
        private const val CHANNEL_ID = "enotone_indexing"
        private const val NOTIFICATION_ID = 4056
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var indexJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startFromUser()
            ACTION_PAUSE -> cancelSession(ManagedScanState.PAUSED)
            ACTION_STOP -> cancelSession(ManagedScanState.STOPPED)
            else -> stopSelf()
        }
        // No automatic background restart without a fresh user action/credentials.
        return START_NOT_STICKY
    }

    private fun startFromUser() {
        if (indexJob != null) return
        val login = ManagedScanRuntime.claim()
        if (login == null) {
            ManagedScanRuntime.update(ManagedScanState.ERROR, terminating = true)
            stopSelf()
            return
        }
        try {
            createChannel()
            enterForeground(buildNotification(null))
        } catch (_: RuntimeException) {
            ManagedScanRuntime.update(ManagedScanState.ERROR, terminating = true)
            stopSelf()
            return
        }

        indexJob = scope.launch(Dispatchers.IO) {
            var stage = "DATABASE_OPEN"
            try {
                val database = openLibraryDatabase(applicationContext)
                try {
                    stage = "INDEX_BATCH"
                    val final = ManagedTagIndexSession(database).run(
                        login.email,
                        login.password,
                        // No Wi-Fi / metered network constraint. Network failures are
                        // handled by the existing bounded WebDAV Range reader.
                        networkAllowed = { true },
                        onProgress = { progress ->
                            ManagedScanRuntime.update(ManagedScanState.RUNNING, progress)
                            withContext(Dispatchers.Main) {
                                val manager = getSystemService(NotificationManager::class.java)
                                if (Build.VERSION.SDK_INT < 33 ||
                                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        manager?.notify(NOTIFICATION_ID, buildNotification(progress))
                                    } catch (_: RuntimeException) {
                                        // The notification is optional telemetry: failures
                                        // must NEVER cancel a healthy tag scan.
                                    }
                                }
                            }
                        },
                    )
                    ManagedScanRuntime.update(
                        if (final.stopCode in setOf("AUTH_REJECTED", "RETRY_BACKOFF"))
                            ManagedScanState.ERROR else ManagedScanState.COMPLETE,
                        final,
                        terminating = true,
                    )
                    stage = "DATABASE_CLOSE"
                } finally {
                    database.close()
                }
            } catch (_: CancellationException) {
                // Pause/stop was set before cancellation; preserve its reason.
            } catch (failure: Exception) {
                // Numeric progress survives, with a safe terminal reason rather
                // than stale RUNNING. No exception message or stack is emitted.
                ManagedScanRuntime.fail(stage, failure)
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun cancelSession(reason: ManagedScanState) {
        // A cancelled Job may still be completing its finally block.
        val hadJob = indexJob != null
        ManagedScanRuntime.update(reason, terminating = hadJob)
        indexJob?.cancel()
        if (!hadJob) stopSelf()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID,
            getString(R.string.managed_index_title),
            NotificationManager.IMPORTANCE_LOW,
        ))
    }

    private fun enterForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, IndexingForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun buildNotification(progress: ManagedIndexProgress?): Notification {
        val label = if (progress == null) getString(R.string.managed_notification_started)
            else getString(
                R.string.managed_notification_progress,
                progress.attempted,
                progress.remaining,
                progress.getRequests,
            )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.managed_index_title))
            .setContentText(label)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.managed_index_pause),
                actionIntent(ACTION_PAUSE, 101),
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.managed_index_stop),
                actionIntent(ACTION_STOP, 102),
            )
            .build()
    }

    // Android 15+ dataSync foreground time budget: stop promptly, do not crash.
    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelSession(ManagedScanState.PAUSED)
        stopSelf()
    }

    override fun onDestroy() {
        val last = ManagedScanRuntime.status.value
        if (last.state == ManagedScanState.RUNNING) {
            ManagedScanRuntime.update(ManagedScanState.PAUSED)
        } else if (last.terminating) {
            // Resume is safe only after this service instance has stopped.
            ManagedScanRuntime.update(last.state, terminating = false)
        }
        indexJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
