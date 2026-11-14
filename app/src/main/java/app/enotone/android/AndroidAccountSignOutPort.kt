package app.enotone.android

import android.content.Context
import android.content.Intent
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.playback.QueueStateFileStore
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android adapter for account cleanup. Album/source hashes are journaled
 * before deleting Room, so a second attempt can finish after process death.
 */
internal class AndroidAccountSignOutPort(
    context: Context,
    private val container: AppContainer,
    private val email: String,
) : AccountSignOutPort {
    private val context = context.applicationContext
    private val io: CoroutineDispatcher = container.io
    private val main: CoroutineDispatcher = container.main
    private val manifest = AccountArtworkManifest(
        File(context.noBackupFilesDir,
            "enotone-signout-" + LibrarySnapshotPlanner.accountKey(email) + ".keys"),
    )

    override suspend fun stopPlayback() {
        AccountPlaybackStop(
            stopBoundService = {
                withContext(main) { container.playbackConnection.stopPlayback() }
            },
            clearQueue = {
                // A service may never have been bound. These files still exist
                // after process death; always erase them before Room/Keystore.
                withContext(io) { QueueStateFileStore(context).clear() }
            },
        ).run()
    }

    override suspend fun stopIndexers() {
        withContext(main) {
            if (ManagedScanRuntime.status.value.let {
                    it.state == ManagedScanState.RUNNING || it.terminating
                }) {
                context.startService(
                    Intent(context, IndexingForegroundService::class.java)
                        .setAction(IndexingForegroundService.ACTION_STOP),
                )
            }
            if (ArtworkScanRuntime.status.value.let {
                    it.state == ArtworkScanState.RUNNING || it.terminating
                }) {
                context.startService(
                    Intent(context, ArtworkIndexingForegroundService::class.java)
                        .setAction(ArtworkIndexingForegroundService.ACTION_STOP),
                )
            }
        }
        // A command returning does NOT mean the service has finished writing.
        val stopped = withTimeoutOrNull(30_000L) {
            ManagedScanRuntime.status.first {
                it.state != ManagedScanState.RUNNING && !it.terminating
            }
            ArtworkScanRuntime.status.first {
                it.state != ArtworkScanState.RUNNING && !it.terminating
            }
            true
        }
        check(stopped == true) { "Indexer did not stop safely" }
    }

    override suspend fun deleteTracks(sourceKey: String) = withContext(io) {
        val dao = container.database.remoteTrackDao()
        if (manifest.read() == null) {
            val rows = dao.allForSource(sourceKey)
            manifest.save(container.artworkPreviewRepository.keysForAccount(
                email, sourceKey, rows,
            ))
        }
        container.database.deleteAccountData(sourceKey)
        Unit
    }

    override suspend fun deleteArtwork(sourceKey: String) {
        if (withContext(io) {
                container.database.remoteTrackDao().countOtherSources(sourceKey)
            } == 0) {
            // Single-account installation: also remove old, no-longer-indexed refs.
            container.artworkPreviewRepository.clearAll()
            check(container.artworkPreviewRepository.storedBytes() == 0L)
        } else {
            val keys = withContext(io) {
                requireNotNull(manifest.read()) { "Missing account cleanup manifest" }
            }
            container.artworkPreviewRepository.removeAccount(keys)
        }
    }

    override suspend fun resetCheckpoints(sourceKey: String) {
        // Tag checkpoints were removed with their Room rows; artwork selections
        // were removed with the references. Preserve global non-PII diagnostics.
        ManagedScanRuntime.status.value = ManagedScanStatus()
        ArtworkScanRuntime.status.value = ArtworkScanStatus()
    }

    override suspend fun deleteCredentials() {
        withContext(io) {
            LocalCredentialVault.clear(context)
            // Only hashed file names are journaled; best-effort removal after
            // successful vault clearing cannot invalidate the completed exit.
            manifest.clear()
        }
    }
}
