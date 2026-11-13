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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android-only foreground lifecycle; the runner owns artwork traversal. */
class ArtworkIndexingForegroundService : Service() {
    companion object {
        const val ACTION_START = "app.enotone.android.artwork.START"
        const val ACTION_PAUSE = "app.enotone.android.artwork.PAUSE"
        const val ACTION_STOP = "app.enotone.android.artwork.STOP"
        private const val CHANNEL = "enotone_artwork"
        private const val NOTIFICATION_ID = 4057
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var indexJob: Job? = null
    private val runner by lazy {
        val repository = ArtworkPreviewRepository(applicationContext)
        ArtworkPreparationRunner(
            port = object : ArtworkPreparationPort<AlbumArtworkGroup> {
                override suspend fun groups(login: StoredWebDavLogin) =
                    ArtworkIndexPlanner.fromStoredLibrary(applicationContext, login.email)

                override suspend fun isIndexed(login: StoredWebDavLogin, group: AlbumArtworkGroup) =
                    repository.isAlbumIndexed(login.email, group)

                override suspend fun prepare(login: StoredWebDavLogin, group: AlbumArtworkGroup) {
                    repository.albumCover(login.email, login.password, group, requestedSide = 192)
                }

                override fun tagsRunning() =
                    ManagedScanRuntime.status.value.state == ManagedScanState.RUNNING

                override suspend fun publish(completed: Int, total: Int) {
                    withContext(Dispatchers.Main) {
                        val manager = getSystemService(NotificationManager::class.java)
                        try {
                            manager?.notify(NOTIFICATION_ID, notification(completed, total))
                        } catch (_: RuntimeException) {
                            // Notification delivery must not interrupt image preparation.
                        }
                    }
                }
            },
            signOutPending = { SignOutMarker.pending(applicationContext) },
        )
    }

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
        if (SignOutMarker.pending(applicationContext)) {
            ArtworkScanRuntime.update(ArtworkScanState.PAUSED, 0, 0)
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
        indexJob = scope.launch {
            try {
                runner.run(login)
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun stopSession(state: ArtworkScanState) {
        val active = indexJob != null
        when (state) {
            ArtworkScanState.PAUSED -> runner.pause(active)
            ArtworkScanState.STOPPED -> runner.stop(active)
            else -> return
        }
        indexJob?.cancel()
        if (!active) stopSelf()
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
