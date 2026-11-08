package app.enotone.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Private on-device catalogue. Never log filenames, remote paths or ETags.
 * sourceKey is a SHA-256 digest of the account identifier; no email/password is stored.
 * id is stable across ETag updates but not across renames (handled as a new file in M1).
 */
@Entity(
    tableName = "remote_tracks",
    indices = [Index(value = ["sourceKey", "encodedPath"], unique = true)],
)
data class RemoteTrackEntity(
    @PrimaryKey val id: String,
    val sourceKey: String,
    val encodedPath: String,
    val fileName: String,
    val remoteEtag: String?,
    val remoteSize: Long?,
    val remoteModified: String?,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val lastSeenEpoch: Long,
    val missingScans: Int = 0,
    /** Nullable checkpoint for the fast-parser outcome; preserved only for unchanged versions. */
    val tagScanCode: String? = null,
)
