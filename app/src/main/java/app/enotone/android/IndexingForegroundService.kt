package app.enotone.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.enotone.library.ManagedIndexProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android-only foreground lifecycle, notification and action intents. */
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
    private val runner by lazy {
        IndexingRunner(
            sessions = RoomIndexingSessionFactory(
                database = { (application as EnoToneApplication).container.database },
                networkAllowed = { true },
            ),
            signOutPending = { SignOutMarker.pending(applicationContext) },
        )
    }

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
        val request = ManagedScanRuntime.claim()
        if (request == null) {
            ManagedScanRuntime.update(ManagedScanState.ERROR, terminating = true)
            stopSelf()
            return
        }
        if (SignOutMarker.pending(applicationContext)) {
            ManagedScanRuntime.update(ManagedScanState.PAUSED)
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
        indexJob = scope.launch {
            try {
                runner.run(request) { progress ->
                    withContext(Dispatchers.Main) {
                        val manager = getSystemService(NotificationManager::class.java)
                        if (Build.VERSION.SDK_INT < 33 ||
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            try {
                                manager?.notify(NOTIFICATION_ID, buildNotification(progress))
                            } catch (_: RuntimeException) {
                                // Notification delivery must not cancel a healthy scan.
                            }
                        }
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun cancelSession(reason: ManagedScanState) {
        val active = indexJob != null
        when (reason) {
            ManagedScanState.PAUSED -> runner.pause(active)
            ManagedScanState.STOPPED -> runner.stop(active)
            else -> return
        }
        indexJob?.cancel()
        if (!active) stopSelf()
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

    override fun onTimeout(startId: Int, fgsType: Int) {
        runner.onTimeout(indexJob != null)
        indexJob?.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        runner.onDestroy()
        indexJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
