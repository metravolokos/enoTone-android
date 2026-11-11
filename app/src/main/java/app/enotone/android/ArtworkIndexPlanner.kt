package app.enotone.android

import android.content.Context
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.OfflineTrackCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Prepared entirely from local Room tags. Never walks WebDAV and never
 * creates per-track image network requests for a known shared album.
 */
internal object ArtworkIndexPlanner {
    suspend fun fromStoredLibrary(
        context: Context, accountEmail: String,
    ): List<AlbumArtworkGroup> = withContext(Dispatchers.IO) {
        val db = openLibraryDatabase(context.applicationContext)
        try {
            val sourceKey = LibrarySnapshotPlanner.accountKey(accountEmail)
            val rows = db.remoteTrackDao().allForSource(sourceKey)
            val playable = rows.filter {
                it.sourceKey == sourceKey && it.missingScans == 0 &&
                    (it.remoteSize ?: 0L) > 0L
            }.associateBy { it.id }
            AlbumArtworkCatalog.build(OfflineTrackCatalog.build(sourceKey, rows)) { id ->
                val item = playable[id] ?: return@build null
                PlayingArtworkTarget(
                    id = item.id,
                    encodedPath = item.encodedPath,
                    remoteSize = item.remoteSize ?: return@build null,
                    remoteEtag = item.remoteEtag,
                )
            }.groups
        } finally {
            db.close()
        }
    }

    suspend fun needsPreparation(
        context: Context, accountEmail: String,
    ): Boolean {
        val repo = ArtworkPreviewRepository(context.applicationContext)
        return fromStoredLibrary(context, accountEmail).any {
            !repo.isAlbumIndexed(accountEmail, it)
        }
    }
}
