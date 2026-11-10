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

    /** Resolve a local track ID only within its active account/snapshot. */
    @Query("""
        SELECT * FROM remote_tracks
        WHERE id = :id AND sourceKey = :sourceKey
          AND missingScans = 0 AND remoteSize > 0
        LIMIT 1
    """)
    suspend fun playableById(id: String, sourceKey: String): RemoteTrackEntity?

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
            albumArtist = :albumArtist,
            year = :year,
            trackNumber = :trackNumber,
            discNumber = :discNumber
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
        year: Int? = null,
        trackNumber: Int? = null,
        discNumber: Int? = null,
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
            -- Refresh failures must not erase previously known good metadata.
            -- Only a successfully parsed ID3/Vorbis payload replaces old tags.
            title = CASE WHEN :code IN ('MP3_ID3V23', 'MP3_ID3V24',
                'MP3_ID3V1', 'FLAC_VORBIS') THEN :title ELSE title END,
            artist = CASE WHEN :code IN ('MP3_ID3V23', 'MP3_ID3V24',
                'MP3_ID3V1', 'FLAC_VORBIS') THEN :artist ELSE artist END,
            album = CASE WHEN :code IN ('MP3_ID3V23', 'MP3_ID3V24',
                'MP3_ID3V1', 'FLAC_VORBIS') THEN :album ELSE album END,
            albumArtist = CASE WHEN :code IN ('MP3_ID3V23', 'MP3_ID3V24',
                'MP3_ID3V1', 'FLAC_VORBIS') THEN :albumArtist ELSE albumArtist END,
            year = CASE WHEN :code IN ('MP3_ID3V23', 'MP3_ID3V24',
                'MP3_ID3V1', 'FLAC_VORBIS') THEN :year ELSE year END,
            trackNumber = CASE WHEN :code IN ('MP3_ID3V23', 'MP3_ID3V24',
                'MP3_ID3V1', 'FLAC_VORBIS') THEN :trackNumber ELSE trackNumber END,
            discNumber = CASE WHEN :code IN ('MP3_ID3V23', 'MP3_ID3V24',
                'MP3_ID3V1', 'FLAC_VORBIS') THEN :discNumber ELSE discNumber END
        WHERE id = :id AND sourceKey = :sourceKey AND encodedPath = :path
          AND remoteEtag IS :etag AND remoteSize IS :size
          AND remoteModified IS :modified AND missingScans = 0
          AND (tagScanCode IS NULL OR tagScanCode LIKE 'RETRY_%')
          AND (tagScanCode LIKE 'RETRY_%' OR
              (title IS NULL AND artist IS NULL
               AND album IS NULL AND albumArtist IS NULL))
    """)
    suspend fun recordFastScanIfUnchanged(
        id: String, sourceKey: String, path: String,
        etag: String?, size: Long?, modified: String?,
        code: String, title: String?, artist: String?,
        album: String?, albumArtist: String?,
        year: Int? = null, trackNumber: Int? = null, discNumber: Int? = null,
    ): Int

    /** Only present files for this hashed account; deterministic ordering is app side. */
    @Query("""
        SELECT * FROM remote_tracks
        WHERE sourceKey = :sourceKey AND missingScans = 0
          AND remoteSize > 0
          AND ((tagScanCode IS NULL AND title IS NULL AND artist IS NULL
                AND album IS NULL AND albumArtist IS NULL)
               OR tagScanCode LIKE 'RETRY_%')
          AND (LOWER(fileName) LIKE '%.mp3' OR LOWER(fileName) LIKE '%.flac')
    """)
    suspend fun pendingFastScans(sourceKey: String): List<RemoteTrackEntity>

    /**
     * Read-only on-device audit. Private filenames and tag values may appear
     * only on the local screen; never in logs, GitHub issues or telemetry.
     */
    @Query("""
        SELECT * FROM remote_tracks
        WHERE sourceKey = :sourceKey AND missingScans = 0
          AND album IS NULL
        ORDER BY fileName COLLATE NOCASE, id
        LIMIT :limit
    """)
    suspend fun tracksWithoutAlbum(sourceKey: String, limit: Int = 30): List<RemoteTrackEntity>

    @Query("""
        SELECT * FROM remote_tracks
        WHERE sourceKey = :sourceKey AND missingScans = 0
          AND albumArtist IS NULL
        ORDER BY fileName COLLATE NOCASE, id
        LIMIT :limit
    """)
    suspend fun tracksWithoutAlbumArtist(sourceKey: String, limit: Int = 30): List<RemoteTrackEntity>

    @Query("""
        SELECT * FROM remote_tracks
        WHERE sourceKey = :sourceKey AND missingScans = 0
          AND (fileName LIKE :pattern OR title LIKE :pattern)
        ORDER BY fileName COLLATE NOCASE, id
        LIMIT :limit
    """)
    suspend fun findTracksForReindex(
        sourceKey: String, pattern: String, limit: Int = 30,
    ): List<RemoteTrackEntity>

    /**
     * Explicit local queue request. Does not erase tag values; a later
     * managed scan will re-fetch this single version with bounded HTTP Range.
     */
    @Query("""
        UPDATE remote_tracks
        SET tagScanCode = 'RETRY_REFRESH'
        WHERE sourceKey = :sourceKey AND id = :id AND missingScans = 0
          AND remoteSize > 0
          AND (LOWER(fileName) LIKE '%.mp3' OR LOWER(fileName) LIKE '%.flac')
    """)
    suspend fun queueTagRefreshById(sourceKey: String, id: String): Int

    /**
     * Explicit all-files refresh queue for the current account. Does not
     * delete or null tags. One atomic UPDATE, no additional Room schema.
     * Actual HTTP requests still require user-started managed indexing.
     */
    @Query("""
        UPDATE remote_tracks
        SET tagScanCode = 'RETRY_REFRESH'
        WHERE sourceKey = :sourceKey AND missingScans = 0
          AND remoteSize > 0
          AND (LOWER(fileName) LIKE '%.mp3' OR LOWER(fileName) LIKE '%.flac')
    """)
    suspend fun queueAllTagRefresh(sourceKey: String): Int

    /** All counts are local; text, paths and private tags are never selected. */
    @Query("""
        SELECT COUNT(*) FROM remote_tracks
        WHERE sourceKey = :sourceKey AND missingScans = 0
          AND tagScanCode IS NOT NULL AND tagScanCode NOT LIKE 'RETRY_%'
          AND tagScanCode NOT LIKE 'FAILED_%'
          AND tagScanCode NOT IN ('INVALID_METADATA', 'FAST_BUDGET_EXCEEDED')
    """)
    suspend fun finishedFastScanCount(sourceKey: String): Int

    /** Distinguish files never attempted from temporary failures after app restart. */
    @Query("""
        SELECT COUNT(*) FROM remote_tracks
        WHERE sourceKey = :sourceKey AND missingScans = 0
          AND tagScanCode LIKE 'RETRY_%'
          AND tagScanCode != 'RETRY_REFRESH'
    """)
    suspend fun retryableFastScanCount(sourceKey: String): Int

    @Query("""
        SELECT COUNT(*) FROM remote_tracks
        WHERE sourceKey = :sourceKey AND missingScans = 0
          AND (tagScanCode LIKE 'FAILED_%'
            OR tagScanCode IN ('INVALID_METADATA', 'FAST_BUDGET_EXCEEDED'))
    """)
    suspend fun permanentFastScanCount(sourceKey: String): Int

    @Query("SELECT COUNT(*) FROM remote_tracks WHERE sourceKey = :sourceKey AND missingScans = 0")
    suspend fun presentCount(sourceKey: String): Int
}
