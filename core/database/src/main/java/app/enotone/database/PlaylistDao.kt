package app.enotone.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface PlaylistDao {
    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE sourceKey = :sourceKey AND kind = :kind LIMIT 1")
    suspend fun bySourceAndKind(sourceKey: String, kind: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE sourceKey = :sourceKey ORDER BY createdEpochMs, id")
    suspend fun allForSource(sourceKey: String): List<PlaylistEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String): Int

    @Query("DELETE FROM playlists WHERE sourceKey = :sourceKey")
    suspend fun deleteAllForSource(sourceKey: String): Int

    @Query("UPDATE playlists SET updatedEpochMs = :updatedEpochMs WHERE id = :playlistId")
    suspend fun touch(playlistId: String, updatedEpochMs: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: PlaylistItemEntity): Long

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteItem(playlistId: String, trackId: String): Int

    @Query("UPDATE playlist_items SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun setPosition(playlistId: String, trackId: String, position: Int): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun lastPosition(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun itemCount(playlistId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_items WHERE playlistId = :playlistId AND trackId = :trackId)")
    fun contains(playlistId: String, trackId: String): Flow<Boolean>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM playlist_items
            INNER JOIN playlists ON playlists.id = playlist_items.playlistId
            WHERE playlists.kind = 'FAVORITES' AND playlist_items.trackId = :trackId
        )
    """)
    fun isFavorite(trackId: String): Flow<Boolean>

    @Query("""
        SELECT playlist_items.trackId AS trackId,
            playlist_items.position AS position,
            playlist_items.addedEpochMs AS addedEpochMs,
            CASE WHEN remote_tracks.missingScans = 0 THEN 1 ELSE 0 END AS present
        FROM playlist_items
        INNER JOIN remote_tracks ON remote_tracks.id = playlist_items.trackId
        WHERE playlist_items.playlistId = :playlistId
        ORDER BY playlist_items.position, playlist_items.addedEpochMs, playlist_items.trackId
    """)
    fun items(playlistId: String): Flow<List<PlaylistTrackItem>>
}
