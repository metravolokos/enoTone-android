package app.enotone.library

import app.enotone.database.EnoToneDatabase
import app.enotone.database.RemoteTrackEntity
import app.enotone.database.SavedTagCoverage

/**
 * Opt-in persistence of metadata from ONE track. The DAO compares the entire
 * stored version atomically, so stale reads cannot overwrite a newer listing.
 * Does not store a password or perform network requests.
 */
class LibraryTagStore(private val database: EnoToneDatabase) {
    /** Local-only inspection; filenames never sent to analytics. */
    suspend fun missingAlbum(accountEmail: String): List<RemoteTrackEntity> =
        database.remoteTrackDao().tracksWithoutAlbum(
            LibrarySnapshotPlanner.accountKey(accountEmail))

    suspend fun missingAlbumArtist(accountEmail: String): List<RemoteTrackEntity> =
        database.remoteTrackDao().tracksWithoutAlbumArtist(
            LibrarySnapshotPlanner.accountKey(accountEmail))

    suspend fun findTracks(accountEmail: String, text: String): List<RemoteTrackEntity> =
        if (text.isBlank()) emptyList()
        else database.remoteTrackDao().findTracksForReindex(
            LibrarySnapshotPlanner.accountKey(accountEmail),
            "%" + text.trim().take(120) + "%",
        )

    /** Queue a specific file for the existing bounded/background reader. */
    suspend fun queueOneForRefresh(accountEmail: String, id: String): Boolean =
        database.remoteTrackDao().queueTagRefreshById(
            LibrarySnapshotPlanner.accountKey(accountEmail), id) == 1

    /** No network and no tag deletion. A managed start is still required. */
    suspend fun queueAllForRefresh(accountEmail: String): Int =
        database.remoteTrackDao().queueAllTagRefresh(
            LibrarySnapshotPlanner.accountKey(accountEmail))

    /** Offline summary only. Account separation is identical to the snapshot store. */
    suspend fun storedCoverage(accountEmail: String): SavedTagCoverage =
        database.remoteTrackDao().storedTagCoverage(
            LibrarySnapshotPlanner.accountKey(accountEmail))


    suspend fun saveIfUnchanged(
        accountEmail: String,
        selected: RemoteTrackEntity,
        tags: AudioTagFields,
    ): Boolean {
        val sourceKey = LibrarySnapshotPlanner.accountKey(accountEmail)
        if (!eligibleForSingleTagWrite(sourceKey, selected)) return false
        return database.remoteTrackDao().saveTagsIfSnapshotMatches(
            id = selected.id,
            sourceKey = sourceKey,
            encodedPath = selected.encodedPath,
            expectedEtag = selected.remoteEtag,
            expectedSize = selected.remoteSize,
            expectedModified = selected.remoteModified,
            title = tags.title,
            artist = tags.artist,
            album = tags.album,
            albumArtist = tags.albumArtist,
        ) == 1
    }
}

/** Cheap precondition; the SQL WHERE is the authoritative concurrent guard. */
internal fun eligibleForSingleTagWrite(
    sourceKey: String,
    selected: RemoteTrackEntity,
): Boolean =
    selected.sourceKey == sourceKey &&
        selected.id == LibrarySnapshotPlanner.trackId(sourceKey, selected.encodedPath) &&
        selected.missingScans == 0 &&
        (selected.remoteSize ?: 0L) > 0L &&
        selected.encodedPath.startsWith("/AllMusic/") &&
        selected.encodedPath.removePrefix("/AllMusic/").isNotEmpty() &&
        !selected.encodedPath.removePrefix("/AllMusic/").contains('/')
