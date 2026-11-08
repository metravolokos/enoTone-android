package app.enotone.library

import app.enotone.database.RemoteTrackEntity
import app.enotone.network.FlatWebDavListing
import app.enotone.network.RemoteAudioFile
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/** Contains only safe numeric statistics for diagnostic reporting. */
data class SnapshotPlan(
    val upserts: List<RemoteTrackEntity>,
    val inserted: Int,
    val changed: Int,
    val unchanged: Int,
    val previouslyMissing: Int,
    val completeForAbsenceTracking: Boolean,
)

object LibrarySnapshotPlanner {
    fun accountKey(email: String): String {
        val normalized = email.trim().lowercase(Locale.ROOT)
        require(normalized.isNotEmpty() && normalized.contains('@')) { "Invalid account identifier" }
        return sha256(normalized)
    }

    /** Identity is independent of ETag/mtime so metadata can survive an unchanged rescan. */
    fun trackId(sourceKey: String, encodedPath: String): String =
        sha256(sourceKey + "\u0000" + encodedPath)

    fun plan(
        sourceKey: String,
        listing: FlatWebDavListing,
        previous: List<RemoteTrackEntity>,
        epoch: Long,
    ): SnapshotPlan {
        require(sourceKey.length == 64 && sourceKey.all { it in "0123456789abcdef" })
        require(epoch > 0)
        val byId = previous.filter { it.sourceKey == sourceKey }.associateBy { it.id }
        val unique = LinkedHashMap<String, RemoteAudioFile>()
        for (file in listing.tracks) {
            // The network parser has already checked origin and flat path.
            // An extra guard avoids persisting accidental nested entries.
            require(file.encodedPath.startsWith("/AllMusic/") &&
                file.encodedPath.removePrefix("/AllMusic/").isNotEmpty() &&
                !file.encodedPath.removePrefix("/AllMusic/").contains('/')) {
                "Invalid flat library entry"
            }
            unique[file.encodedPath] = file
        }
        var inserted = 0
        var changed = 0
        var unchanged = 0
        val updated = unique.values.map { file ->
            val id = trackId(sourceKey, file.encodedPath)
            val old = byId[id]
            val sameVersion = old != null && sameRemoteVersion(old, file)
            when {
                old == null -> inserted++
                sameVersion -> unchanged++
                else -> changed++
            }
            RemoteTrackEntity(
                id = id,
                sourceKey = sourceKey,
                encodedPath = file.encodedPath,
                fileName = file.fileName,
                remoteEtag = file.etag,
                remoteSize = file.sizeBytes,
                remoteModified = file.lastModified,
                title = if (sameVersion) old?.title else null,
                artist = if (sameVersion) old?.artist else null,
                album = if (sameVersion) old?.album else null,
                albumArtist = if (sameVersion) old?.albumArtist else null,
                lastSeenEpoch = epoch,
                missingScans = 0,
            )
        }
        val complete = listing.invalidEntries == 0 &&
            listing.skippedNested == 0 && listing.skippedDirectories == 0
        return SnapshotPlan(
            updated, inserted, changed, unchanged,
            previous.count { it.sourceKey == sourceKey && it.id !in updatedIds(updated) },
            complete,
        )
    }

    private fun updatedIds(items: List<RemoteTrackEntity>): Set<String> =
        items.mapTo(HashSet(items.size)) { it.id }

    private fun sameRemoteVersion(old: RemoteTrackEntity, new: RemoteAudioFile): Boolean {
        if (old.remoteEtag != null && new.etag != null) {
            return old.remoteEtag == new.etag
        }
        return old.remoteSize != null && new.sizeBytes != null &&
            old.remoteModified != null && new.lastModified != null &&
            old.remoteSize == new.sizeBytes && old.remoteModified == new.lastModified
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
