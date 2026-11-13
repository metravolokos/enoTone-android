package app.enotone.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["sourceKey"]), Index(value = ["sourceKey", "kind"])],
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val sourceKey: String,
    val name: String,
    val kind: String,
    val createdEpochMs: Long,
    val updatedEpochMs: Long,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index(value = ["trackId"]), Index(value = ["playlistId", "position"])],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
        androidx.room.ForeignKey(
            entity = RemoteTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistItemEntity(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedEpochMs: Long,
)

data class PlaylistTrackItem(
    val trackId: String,
    val position: Int,
    val addedEpochMs: Long,
    val present: Boolean,
)
