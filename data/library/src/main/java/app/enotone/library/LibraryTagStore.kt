package app.enotone.library

import app.enotone.database.EnoToneDatabase
import app.enotone.database.RemoteTrackEntity

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
            year = tags.year,
            trackNumber = tags.trackNumber,
            discNumber = tags.discNumber,
            genre = tags.genre,
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
