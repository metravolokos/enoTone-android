package app.enotone.playlist

import androidx.room.withTransaction
import app.enotone.database.EnoToneDatabase
import app.enotone.database.PlaylistEntity
import app.enotone.database.PlaylistItemEntity
import app.enotone.database.PlaylistTrackItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/** Persistence boundary for the playlist model; it owns no UI or artwork files. */
class PlaylistRepository(
    private val database: EnoToneDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    /** The transaction serializes concurrent first-use calls on the process Room instance. */
    suspend fun favorites(sourceKey: String): PlaylistEntity = withContext(io) {
        require(sourceKey.isNotBlank())
        database.withTransaction { getOrCreateFavorites(sourceKey) }
    }

    suspend fun addToFavorites(sourceKey: String, trackId: String): Boolean = withContext(io) {
        require(sourceKey.isNotBlank() && trackId.isNotBlank())
        database.withTransaction {
            val track = database.remoteTrackDao().playableById(trackId, sourceKey)
                ?: return@withTransaction false
            val playlist = getOrCreateFavorites(sourceKey)
            val dao = database.playlistDao()
            val addedAt = nowEpochMs()
            val inserted = dao.insertItem(
                PlaylistItemEntity(
                    playlistId = playlist.id,
                    trackId = track.id,
                    position = dao.lastPosition(playlist.id) + 1,
                    addedEpochMs = addedAt,
                ),
            ) != -1L
            if (inserted) dao.touch(playlist.id, addedAt)
            inserted
        }
    }

    suspend fun removeFromFavorites(sourceKey: String, trackId: String): Boolean = withContext(io) {
        require(sourceKey.isNotBlank() && trackId.isNotBlank())
        database.withTransaction {
            val dao = database.playlistDao()
            val playlist = dao.bySourceAndKind(sourceKey, FAVORITES_KIND)
                ?: return@withTransaction false
            val removed = dao.deleteItem(playlist.id, trackId) > 0
            if (removed) dao.touch(playlist.id, nowEpochMs())
            removed
        }
    }

    fun isFavorite(trackId: String): Flow<Boolean> =
        database.playlistDao().isFavorite(trackId)

    fun items(playlistId: String): Flow<List<PlaylistTrackItem>> =
        database.playlistDao().items(playlistId)

    private suspend fun getOrCreateFavorites(sourceKey: String): PlaylistEntity {
        val dao = database.playlistDao()
        dao.bySourceAndKind(sourceKey, FAVORITES_KIND)?.let { return it }
        val now = nowEpochMs()
        val created = PlaylistEntity(
            id = UUID.randomUUID().toString(),
            sourceKey = sourceKey,
            name = FAVORITES_NAME,
            kind = FAVORITES_KIND,
            createdEpochMs = now,
            updatedEpochMs = now,
        )
        dao.insertPlaylist(created)
        return created
    }

    private companion object {
        const val FAVORITES_KIND = "FAVORITES"
        const val FAVORITES_NAME = "Избранное"
    }
}
