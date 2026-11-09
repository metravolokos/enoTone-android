package app.enotone.library

import app.enotone.database.EnoToneDatabase
import app.enotone.database.MissingAlbumDiagnostic

/**
 * Explicit local-on-device maintenance. Track names stay on the device,
 * while public diagnostic reports remain counts only.
 *
 * Refresh scheduling NEVER deletes any stored tags. The existing managed
 * dataSync foreground service processes RETRY_REFRESH using the same four
 * parallel readers and the same bounded WebDAV Range limits.
 */
class TagMaintenanceStore(private val database: EnoToneDatabase) {
    suspend fun missingAlbumFiles(email: String): List<MissingAlbumDiagnostic> =
        database.remoteTrackDao().missingAlbums(
            LibrarySnapshotPlanner.accountKey(email), limit = 30,
        )

    suspend fun scheduleOne(email: String, id: String): Boolean {
        if (id.isBlank()) return false
        val key = LibrarySnapshotPlanner.accountKey(email)
        return database.remoteTrackDao().requestTagRefresh(id, key) == 1
    }

    suspend fun scheduleAll(email: String): Int =
        database.remoteTrackDao().requestFullTagRefresh(
            LibrarySnapshotPlanner.accountKey(email),
        )

    suspend fun queuedCount(email: String): Int =
        database.remoteTrackDao().queuedTagRefreshCount(
            LibrarySnapshotPlanner.accountKey(email),
        )
}
