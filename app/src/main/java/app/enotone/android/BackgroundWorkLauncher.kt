package app.enotone.android

import android.content.Context
import android.content.Intent
import app.enotone.library.ManagedIndexMode
import kotlinx.coroutines.flow.StateFlow

/** Секреты передаются службам только через существующий RAM-handoff. */
internal interface BackgroundWorkLauncher {
    val indexing: StateFlow<ManagedScanStatus>
    val artwork: StateFlow<ArtworkScanStatus>
    fun startIndexing(login: StoredWebDavLogin, mode: ManagedIndexMode): Boolean
    fun startArtwork(login: StoredWebDavLogin): Boolean
    fun indexingAction(action: WorkAction)
    fun artworkAction(action: WorkAction)
}

internal enum class WorkAction { PAUSE, STOP }

internal class AndroidBackgroundWorkLauncher(private val context: Context) : BackgroundWorkLauncher {
    override val indexing: StateFlow<ManagedScanStatus> = ManagedScanRuntime.status
    override val artwork: StateFlow<ArtworkScanStatus> = ArtworkScanRuntime.status

    override fun startIndexing(login: StoredWebDavLogin, mode: ManagedIndexMode): Boolean {
        if (!ManagedScanRuntime.prepare(login.email, login.password, mode)) return false
        try {
            context.startForegroundService(Intent(context, IndexingForegroundService::class.java)
                .setAction(IndexingForegroundService.ACTION_START))
        } catch (failure: RuntimeException) {
            ManagedScanRuntime.update(ManagedScanState.ERROR)
            throw failure
        }
        return true
    }

    override fun startArtwork(login: StoredWebDavLogin): Boolean {
        if (!ArtworkScanRuntime.prepare(login.email, login.password)) return false
        try {
            context.startForegroundService(Intent(context, ArtworkIndexingForegroundService::class.java)
                .setAction(ArtworkIndexingForegroundService.ACTION_START))
        } catch (failure: RuntimeException) {
            ArtworkScanRuntime.update(ArtworkScanState.ERROR, 0, 0)
            throw failure
        }
        return true
    }

    override fun indexingAction(action: WorkAction) {
        val name = when (action) {
            WorkAction.PAUSE -> IndexingForegroundService.ACTION_PAUSE
            WorkAction.STOP -> IndexingForegroundService.ACTION_STOP
        }
        context.startService(Intent(context, IndexingForegroundService::class.java).setAction(name))
    }

    override fun artworkAction(action: WorkAction) {
        val name = when (action) {
            WorkAction.PAUSE -> ArtworkIndexingForegroundService.ACTION_PAUSE
            WorkAction.STOP -> ArtworkIndexingForegroundService.ACTION_STOP
        }
        context.startService(Intent(context, ArtworkIndexingForegroundService::class.java).setAction(name))
    }
}
