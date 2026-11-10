package app.enotone.library

import androidx.room.withTransaction
import app.enotone.database.EnoToneDatabase
import app.enotone.database.RemoteTrackEntity

/**
 * An explicit user-selected scope, never inferred from a folder structure:
 * /AllMusic is flat and Artist and Album Artist are independent saved tags.
 * Actual menus for these group actions belong to a later UI iteration.
 */
sealed interface LibraryReindexScope {
    data object EntireLibrary : LibraryReindexScope
    data class Album(val groupKey: String) : LibraryReindexScope
    data class Artist(val groupKey: String) : LibraryReindexScope
    data class AlbumArtist(val groupKey: String) : LibraryReindexScope
}

/** Only transient local data; no credentials, network access or Room migration. */
data class ReindexArtworkSource(
    val id: String,
    val encodedPath: String,
    val remoteSize: Long,
    val remoteEtag: String?,
)

data class LibraryReindexQueueResult(
    val queued: Int,
    /** Source references to invalidate in the existing private artwork cache. */
    val artworkSources: List<ReindexArtworkSource>,
)

/**
 * This pure selection uses the SAME tag-derived keys as the visible UI.
 * No album-name-only matches (which could mix different Album Artists), no
 * automatic fallback from track Artist to Album Artist on artist pages.
 */
object LibraryReindexSelection {
    fun select(
        sourceKey: String,
        stored: List<RemoteTrackEntity>,
        scope: LibraryReindexScope,
    ): List<RemoteTrackEntity> {
        val eligible = stored.filter { item ->
            item.sourceKey == sourceKey && item.missingScans == 0 &&
                (item.remoteSize ?: 0L) > 0L &&
                (item.fileName.endsWith(".mp3", ignoreCase = true) ||
                    item.fileName.endsWith(".flac", ignoreCase = true))
        }
        if (scope == LibraryReindexScope.EntireLibrary) return eligible

        val tracks = OfflineTrackCatalog.build(sourceKey, eligible)
        val selectedIds = when (scope) {
            is LibraryReindexScope.Album -> {
                require(scope.groupKey.startsWith("album:"))
                OfflineLibraryGrouping.albums(tracks).firstOrNull {
                    it.key == scope.groupKey
                }?.trackIds.orEmpty()
            }
            is LibraryReindexScope.Artist -> {
                require(scope.groupKey.startsWith("artist:"))
                OfflineLibraryGrouping.artists(tracks).firstOrNull {
                    it.key == scope.groupKey
                }?.trackIds.orEmpty()
            }
            is LibraryReindexScope.AlbumArtist -> {
                require(scope.groupKey.startsWith("album-artist:"))
                OfflineLibraryGrouping.albumArtists(tracks).firstOrNull {
                    it.key == scope.groupKey
                }?.trackIds.orEmpty()
            }
            LibraryReindexScope.EntireLibrary -> emptySet()
        }
        return eligible.filter { it.id in selectedIds }
    }
}

/**
 * One atomic local preparation. The caller explicitly confirms the scope,
 * then starts the existing bounded/resumable foreground indexer.
 *
 * Every old tag value remains intact until successful parser CAS. Group
 * updates use <=400 SQL parameters per batch to avoid SQLite's bind limit.
 * This method never sends HTTP and does not start work after process restart.
 */
class LibraryReindexCoordinator(private val database: EnoToneDatabase) {
    suspend fun queue(accountEmail: String, scope: LibraryReindexScope): LibraryReindexQueueResult {
        val sourceKey = LibrarySnapshotPlanner.accountKey(accountEmail)
        return database.withTransaction {
            val dao = database.remoteTrackDao()
            val selected = LibraryReindexSelection.select(
                sourceKey, dao.allForSource(sourceKey), scope)
            if (selected.isEmpty()) {
                return@withTransaction LibraryReindexQueueResult(0, emptyList())
            }
            val queued = if (scope == LibraryReindexScope.EntireLibrary) {
                dao.queueAllTagRefresh(sourceKey)
            } else {
                selected.map { it.id }.chunked(MAX_SQL_IDS_PER_BATCH).sumOf { ids ->
                    dao.queueTagRefreshByIds(sourceKey, ids)
                }
            }
            LibraryReindexQueueResult(
                queued = queued,
                artworkSources = selected.mapNotNull { item ->
                    val size = item.remoteSize ?: return@mapNotNull null
                    ReindexArtworkSource(
                        item.id, item.encodedPath, size, item.remoteEtag)
                },
            )
        }
    }

    companion object {
        const val MAX_SQL_IDS_PER_BATCH = 400
    }
}
