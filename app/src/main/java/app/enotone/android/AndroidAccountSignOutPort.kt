package app.enotone.android

import android.content.Context
import android.content.Intent
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.playback.QueueStateFileStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Android adapter for account cleanup. Album/source hashes are journaled
 * before deleting Room, so a second attempt can finish after process death.
 */
/** Upper bound for awaiting the service erase; a stuck service must not block sign-out forever. */
private const val ERASE_TIMEOUT_SECONDS = 30L

internal class AndroidAccountSignOutPort(
    context: Context,
    private val container: AppContainer,
    private val email: String,
) : AccountSignOutPort {
    private val context = context.applicationContext
    private val io: CoroutineDispatcher = container.io
    private val main: CoroutineDispatcher = container.main
    private val sourceKey = LibrarySnapshotPlanner.accountKey(email)
    private val manifest = AccountArtworkManifest.forSource(context.noBackupFilesDir, sourceKey)

    override suspend fun stopPlayback() {
        AccountPlaybackStop(
            stopBoundService = {
                // Returns false when no service is bound; the erase step is then skipped
                // and only the queue files are cleared below.
                withContext(main) { container.playbackConnection.stopPlayback() }
            },
            eraseAudioData = {
                // Awaiting the service is what enforces ADR-0080: the Keystore is cleared
                // only after the account's audio is actually gone. A failed erase throws,
                // which stops sign-out at PLAYBACK and keeps the vault for a retry.
                val erased = withContext(main) {
                    container.playbackConnection.stopPlaybackAndEraseData()
                }
                // get() blocks this IO thread until the service finished deleting;
                // the main thread is never blocked, and the timeout bounds a stuck service.
                withContext(io) { erased.get(ERASE_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
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
        require(sourceKey == this@AndroidAccountSignOutPort.sourceKey)
        val dao = container.database.remoteTrackDao()
        manifest.ensure {
            val rows = dao.allForSource(sourceKey)
            container.artworkPreviewRepository.keysForAccount(email, sourceKey, rows)
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
        // Reset only after account data is gone and before the Keystore is cleared.
        FirstRunPreferences(context).resetAfterSignOut()
    }

    override suspend fun markCleanupComplete() = withContext(io) {
        // The marker is the durable proof required when the process dies during vault clear.
        SignOutMarker.markCleanupComplete(context)
    }

    override suspend fun deleteCredentials() {
        withContext(io) {
            LocalCredentialVault.clear(context)
            // Keep the sign-out marker until this account-scoped manifest is gone.
            manifest.clear()
        }
    }
}
