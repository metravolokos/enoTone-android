package app.enotone.library

import androidx.room.withTransaction
import app.enotone.database.EnoToneDatabase
import app.enotone.database.RemoteTrackEntity
import app.enotone.database.TrackSearchEntity

/**
 * The FTS4 table is disposable derived data, never authoritative for music or artwork.
 * Every read can repair a stale/empty index without WebDAV. One transaction makes a
 * rebuilt index visible atomically to concurrent queries.
 */
class TrackSearchIndex(private val database: EnoToneDatabase) {
    private fun entry(row: RemoteTrackEntity): TrackSearchEntity = TrackSearchEntity(
        trackId = row.id,
        sourceKey = row.sourceKey,
        text = LibraryText.searchKey(
            listOfNotNull(
                row.title, row.artist, row.album, row.albumArtist, row.genre,
                row.fileName.substringBeforeLast('.', row.fileName),
            ).joinToString(" "),
        ),
    )

    /** Repairs the initial empty v6 index and legacy tag writes missed by a caller. */
    suspend fun reconcile(sourceKey: String): List<RemoteTrackEntity> =
        database.withTransaction {
            // Snapshot and FTS repair share one transaction. A background tag write
            // cannot slip between the read and repair and reintroduce stale text.
            val tracks = database.remoteTrackDao().allForSource(sourceKey)
            val dao = database.trackSearchDao()
            val stored = dao.allForSource(sourceKey).groupBy { it.trackId }
            val fresh = tracks.map(::entry)
            val expectedIds = fresh.mapTo(HashSet(fresh.size)) { it.trackId }
            val stale = stored.keys.filterNot { it in expectedIds }
            if (stale.isNotEmpty()) dao.deleteByIds(sourceKey, stale)
            val changed = fresh.filter { row ->
                val old = stored[row.trackId]
                old?.size != 1 || old.single().text != row.text
            }
            changed.chunked(BATCH_SIZE).forEach { batch ->
                dao.deleteByIds(sourceKey, batch.map { it.trackId })
                dao.insertAll(batch)
            }
            tracks
        }

    /** Refresh just the changed track after tag writing (no network or re-scan). */
    suspend fun refreshById(sourceKey: String, id: String) {
        database.withTransaction {
            val dao = database.trackSearchDao()
            val row = database.remoteTrackDao().byId(sourceKey, id)
            dao.deleteByIds(sourceKey, listOf(id))
            if (row != null) dao.insertAll(listOf(entry(row)))
        }
    }

    /** Snapshot plan explicitly identifies added/updated rows; no wholesale rewrite. */
    suspend fun updateRows(sourceKey: String, rows: List<RemoteTrackEntity>) {
        if (rows.isEmpty()) return
        database.withTransaction {
            val dao = database.trackSearchDao()
            rows.filter { it.sourceKey == sourceKey }.chunked(BATCH_SIZE).forEach { batch ->
                dao.deleteByIds(sourceKey, batch.map { it.id })
                dao.insertAll(batch.map(::entry))
            }
        }
    }

    companion object {
        private const val BATCH_SIZE = 300
    }
}
