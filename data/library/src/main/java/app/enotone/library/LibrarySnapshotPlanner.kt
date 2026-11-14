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
    /** Идентификаторы с новой версией файла (без новых): по ним сбрасываются ссылки на обложки. */
    val changedIds: List<String> = emptyList(),
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
        nowEpochMs: Long = System.currentTimeMillis(),
    ): SnapshotPlan {
        require(sourceKey.length == 64 && sourceKey.all { it in "0123456789abcdef" })
        require(epoch > 0)
        val byId = previous.filter { it.sourceKey == sourceKey }.associateBy { it.id }
        // Первый импорт аккаунта — вся медиатека «добавлена до учёта», а не «недавно добавлена».
        val firstImport = byId.isEmpty()
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
        val changedIds = ArrayList<String>()
        val updated = unique.values.map { file ->
            val id = trackId(sourceKey, file.encodedPath)
            val old = byId[id]
            val sameVersion = old != null && sameRemoteVersion(old, file)
            when {
                old == null -> inserted++
                sameVersion -> unchanged++
                else -> {
                    changed++
                    changedIds += id
                }
            }
            // Прежняя строка (даже с null после миграции v5) сохраняет свою дату: иначе после
            // первого обновления вся старая медиатека стала бы «недавно добавленной».
            val firstSeen = if (old != null) old.firstSeenEpochMs else nowEpochMs.takeUnless { firstImport }
            entityFor(sourceKey, id, file, old.takeIf { sameVersion }, epoch, firstSeen)
        }
        val complete = listing.invalidEntries == 0 &&
            listing.skippedNested == 0 && listing.skippedDirectories == 0
        val seenIds = updated.mapTo(HashSet(updated.size)) { it.id }
        return SnapshotPlan(
            updated, inserted, changed, unchanged,
            previous.count { it.sourceKey == sourceKey && it.id !in seenIds },
            complete,
            changedIds,
        )
    }

    /**
     * Строка Room для файла из листинга: теги, длительность и отметка разбора переносятся только
     * с той же версии файла ([kept]); дату добавления [firstSeen] вычисляет [plan].
     */
    @Suppress("LongParameterList") // Все шесть — поля одной строки Room; объект-обёртка ничего не упростит.
    private fun entityFor(
        sourceKey: String,
        id: String,
        file: RemoteAudioFile,
        kept: RemoteTrackEntity?,
        epoch: Long,
        firstSeen: Long?,
    ) = RemoteTrackEntity(
        id = id,
        sourceKey = sourceKey,
        encodedPath = file.encodedPath,
        fileName = file.fileName,
        remoteEtag = file.etag,
        remoteSize = file.sizeBytes,
        remoteModified = file.lastModified,
        title = kept?.title,
        artist = kept?.artist,
        album = kept?.album,
        albumArtist = kept?.albumArtist,
        year = kept?.year,
        trackNumber = kept?.trackNumber,
        discNumber = kept?.discNumber,
        genre = kept?.genre,
        durationMs = kept?.durationMs,
        lastSeenEpoch = epoch,
        missingScans = 0,
        tagScanCode = kept?.tagScanCode,
        firstSeenEpochMs = firstSeen,
    )

    private fun sameRemoteVersion(old: RemoteTrackEntity, new: RemoteAudioFile): Boolean {
        // A WebDAV ETag is useful, but not proof when the server accidentally
        // keeps it after rewriting a file. Any comparable size/mtime change
        // invalidates old metadata even when both ETags still match.
        if (old.remoteSize != null && new.sizeBytes != null &&
            old.remoteSize != new.sizeBytes) return false
        if (old.remoteModified != null && new.lastModified != null &&
            old.remoteModified != new.lastModified) return false
        if (old.remoteEtag != null && new.etag != null) {
            return old.remoteEtag == new.etag
        }
        // With missing ETags we require BOTH stable size and modified time;
        // weaker evidence would incorrectly preserve an old tag checkpoint.
        return old.remoteSize != null && new.sizeBytes != null &&
            old.remoteModified != null && new.lastModified != null &&
            old.remoteSize == new.sizeBytes && old.remoteModified == new.lastModified
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        val alphabet = "0123456789abcdef"
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val unsigned = byte.toInt() and 0xff
                append(alphabet[unsigned ushr 4])
                append(alphabet[unsigned and 0x0f])
            }
        }
    }
}
