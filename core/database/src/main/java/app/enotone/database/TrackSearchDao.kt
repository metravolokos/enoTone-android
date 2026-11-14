package app.enotone.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TrackSearchDao {
    @Query("SELECT * FROM track_search WHERE sourceKey = :sourceKey")
    suspend fun allForSource(sourceKey: String): List<TrackSearchEntity>

    @Query("DELETE FROM track_search WHERE sourceKey = :sourceKey")
    suspend fun deleteForSource(sourceKey: String): Int

    @Query("DELETE FROM track_search WHERE sourceKey = :sourceKey AND trackId IN (:ids)")
    suspend fun deleteByIds(sourceKey: String, ids: List<String>): Int

    @Insert
    suspend fun insertAll(items: List<TrackSearchEntity>)

    @Query("""
        SELECT track_search.trackId
        FROM track_search JOIN remote_tracks ON remote_tracks.id = track_search.trackId
        WHERE track_search.sourceKey = :sourceKey
          AND remote_tracks.sourceKey = :sourceKey
          AND remote_tracks.missingScans = 0
          AND track_search MATCH :expression
    """)
    suspend fun searchTrackIds(sourceKey: String, expression: String): List<String>
}
