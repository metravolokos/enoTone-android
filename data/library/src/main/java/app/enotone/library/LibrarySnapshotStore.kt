package app.enotone.library

import androidx.room.withTransaction
import app.enotone.database.EnoToneDatabase
import app.enotone.network.FlatWebDavListing

/**
 * Accepts only a successfully parsed WebDAV listing. Atomic upsert, never DELETE.
 * Missing file markers are conservative: incomplete listings cannot mark files missing.
 * Passwords are not accepted or persisted in this layer.
 */
class LibrarySnapshotStore(private val database: EnoToneDatabase) {
    suspend fun save(accountEmail: String, listing: FlatWebDavListing): SnapshotPlan {
        val sourceKey = LibrarySnapshotPlanner.accountKey(accountEmail)
        return database.withTransaction {
            val dao = database.remoteTrackDao()
            val previous = dao.allForSource(sourceKey)
            val epoch = dao.latestEpoch(sourceKey) + 1
            val plan = LibrarySnapshotPlanner.plan(sourceKey, listing, previous, epoch)
            dao.upsertAll(plan.upserts)
            if (plan.completeForAbsenceTracking) {
                dao.markMissing(sourceKey, epoch)
            }
            plan
        }
    }
}
