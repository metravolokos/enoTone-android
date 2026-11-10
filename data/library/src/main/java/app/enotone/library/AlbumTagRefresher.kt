package app.enotone.library

import app.enotone.database.EnoToneDatabase

/** A single explicit, foreground-only batch for ONE locally selected album. */
data class AlbumTagRefreshReport(
    val checkedIds: Set<String>,
    val progress: PilotIndexProgress,
)

/**
 * Does not start the global 3,590-file indexer or list WebDAV.
 * Only at most 10 opaque track IDs selected from a currently open album
 * are queued and passed to the existing bounded serial Range reader.
 *
 * All other pending Room rows are excluded from candidate selection. On
 * transient failure the refresh checkpoint remains retryable, and prior
 * artist/album/title tags remain intact. No credential persistence.
 */
class AlbumTagRefresher(private val database: EnoToneDatabase) {
    suspend fun refresh(
        email: String,
        password: String,
        trackIds: List<String>,
    ): AlbumTagRefreshReport {
        require(trackIds.size in 1..MAX_FILES_PER_TAP)
        require(trackIds.distinct().size == trackIds.size)
        require(email.isNotBlank() && password.isNotBlank())
        val sourceKey = LibrarySnapshotPlanner.accountKey(email)
        database.remoteTrackDao().queueTagRefreshByIds(sourceKey, trackIds)
        val completed = linkedSetOf<String>()
        val progress = PilotTagIndexer(database).run(
            email = email,
            password = password,
            count = 10, // fixed existing 30 GET / 2.5MiB batch ceiling
            targetIds = trackIds.toSet(),
            stopOnRetryableFailure = true,
            onFileCompleted = { id -> completed.add(id) },
        )
        return AlbumTagRefreshReport(completed.toSet(), progress)
    }

    companion object {
        const val MAX_FILES_PER_TAP = 10
    }
}
