package app.enotone.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * Rebuildable normalized search index; not a content-synced view of remote_tracks.
 * Track identities and account source hashes are stored but not FTS-tokenized.
 */
@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    notIndexed = ["trackId", "sourceKey"],
)
@Entity(tableName = "track_search")
data class TrackSearchEntity(
    val trackId: String,
    val sourceKey: String,
    val text: String,
)
