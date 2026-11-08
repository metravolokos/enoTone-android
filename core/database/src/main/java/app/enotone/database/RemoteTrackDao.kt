package app.enotone.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * Aggregated local coverage for one account only. Never expose tag values,
 * file names, paths or ETags to the diagnostic screen.
 */
data class SavedTagCoverage(
    val presentTracks: Int,
    val tracksWithAnyTag: Int,
    val tracksWithTitle: Int,
    val tracksWithArtist: Int,
    val tracksWithAlbum: Int,
    val tracksWithAlbumArtist: Int,
)

@Dao
interface RemoteTrackDao {
    @Query("SELECT * FROM remote_tracks WHERE sourceKey = :sourceKey")
    suspend fun allForSource(sourceKey: String): List<RemoteTrackEntity>

    @Query("SELECT COALESCE(MAX(lastSeenEpoch), 0) FROM remote_tracks WHERE sourceKey = :sourceKey")
    suspend fun latestEpoch(sourceKey: String): Long

    @Upsert
    suspend fun upsertAll(items: List<RemoteTrackEntity>)

    // No automatic deletion: a transient listing must never erase the local library.
    // The absence counter is incremented only for fully parsed, complete listings.
    @Query("""
        UPDATE remote_tracks
        SET missingScans = missingScans + 1
        WHERE sourceKey = :sourceKey AND lastSeenEpoch != :seenEpoch
    """)
    suspend fun markMissing(sourceKey: String, seenEpoch: Long)

    /** Atomic compare-and-set: prevents stale one-track tag updates after a rescan. */
    @Query("""
        UPDATE remote_tracks
        SET title = :title,
            artist = :artist,
            album = :album,
            albumArtist = :albumArtist
        WHERE id = :id
          AND sourceKey = :sourceKey
          AND encodedPath = :encodedPath
          AND remoteEtag IS :expectedEtag
          AND remoteSize IS :expectedSize
          AND remoteModified IS :expectedModified
          AND missingScans = 0
    """)
    suspend fun saveTagsIfSnapshotMatches(
        id: String,
        sourceKey: String,
        encodedPath: String,
        expectedEtag: String?,
        expectedSize: Long?,
        expectedModified: String?,
        title: String?,
        artist: String?,
        album: String?,
        albumArtist: String?,
    ): Int

    /** One local SQLite query; no network, no tag values, no per-track data returned. */
    @Query("""
        SELECT
          COUNT(*) AS presentTracks,
          COALESCE(SUM(CASE WHEN title IS NOT NULL OR artist IS NOT NULL
              OR album IS NOT NULL OR albumArtist IS NOT NULL THEN 1 ELSE 0 END), 0)
              AS tracksWithAnyTag,
          COALESCE(SUM(CASE WHEN title IS NOT NULL THEN 1 ELSE 0 END), 0)
              AS tracksWithTitle,
          COALESCE(SUM(CASE WHEN artist IS NOT NULL THEN 1 ELSE 0 END), 0)
              AS tracksWithArtist,
          COALESCE(SUM(CASE WHEN album IS NOT NULL THEN 1 ELSE 0 END), 0)
              AS tracksWithAlbum,
          COALESCE(SUM(CASE WHEN albumArtist IS NOT NULL THEN 1 ELSE 0 END), 0)
              AS tracksWithAlbumArtist
        FROM remote_tracks
        WHERE sourceKey = :sourceKey AND missingScans = 0
    """)
    suspend fun storedTagCoverage(sourceKey: String): SavedTagCoverage

    /**
     * Record a terminal fast-parser result atomically, only for the same source,
     * snapshot version and an untouched row. Failures do not clobber existing tags.
     * Room v2 adds tagScanCode; nullable on old installations until first attempt.
     */
    @Query("""
        UPDATE remote_tracks
        SET tagScanCode = :code,
            title = :title,
            artist = :artist,
            album = :album,
            albumArtist = :albumArtist
        WHERE id = :id AND sourceKey = :sourceKey AND encodedPath = :path
          AND remoteEtag IS :etag AND remoteSize IS :size
          AND remoteModified IS :modified AND missingScans = 0
          AND (tagScanCode IS NULL OR tagScanCode LIKE 'RETRY_%')
          AND title IS NULL AND artist IS NULL
          AND album IS NULL AND albumArtist IS NULL
    """)
    suspend fun recordFastScanIfUnchanged(
        id: String, sourceKey: String, path: String,
        etag: String?, size: Long?, modified: String?,
        code: String, title: String?, artist: String?,
        album: String?, albumArtist: String?,
    ): Int

    /** Only present files for this hashed account; deterministic ordering is app side. */
    @Query("""
        SELECT * FROM remote_tracks
        WHERE sourceKey = :sourceKey AND missingScans = 0
          AND remoteSize > 0
          AND (tagScanCode IS NULL OR tagScanCode LIKE 'RETRY_%')
          AND title IS NULL AND artist IS NULL
          AND album IS NULL AND albumArtist IS NULL
          AND (LOWER(fileName) LIKE '%.mp3' OR LOWER(fileName) LIKE '%.flac')
    """)
    suspend fun pendingFastScans(sourceKey: String): List<RemoteTrackEntity>

    /** All counts are local; text, paths and private tags are never selected. */
    @Query("""
        SELECT COUNT(*) FROM remote_tracks
        WHERE sourceKey = :sourceKey AND missingScans = 0
          AND tagScanCode IS NOT NULL AND tagScanCode NOT LIKE 'RETRY_%'
    """)
    suspend fun finishedFastScanCount(sourceKey: String): Int

    @Query("SELECT COUNT(*) FROM remote_tracks WHERE sourceKey = :sourceKey AND missingScans = 0")
    suspend fun presentCount(sourceKey: String): Int
}
