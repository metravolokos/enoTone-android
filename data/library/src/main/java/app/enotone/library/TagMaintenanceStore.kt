package app.enotone.library

import app.enotone.database.EnoToneDatabase
import app.enotone.database.MissingAlbumDiagnostic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Explicit local-on-device maintenance. Track names stay on the device,
 * while public diagnostic reports remain counts only.
 *
 * Refresh scheduling NEVER deletes any stored tags. The existing managed
 * dataSync foreground service processes RETRY_REFRESH using the same four
 * parallel readers and the same bounded WebDAV Range limits.
 */
/**
 * A one-file live source check; no database writes and no raw tag values.
 * An unsuccessful parser/read is NOT evidence that ALBUM is absent.
 */
data class AlbumSourceProbe(
    val statusCode: String,
    val complete: Boolean,
    val albumEmbedded: Boolean,
    val getRequests: Int,
)

class TagMaintenanceStore(private val database: EnoToneDatabase) {
    suspend fun missingAlbumFiles(email: String): List<MissingAlbumDiagnostic> =
        database.remoteTrackDao().missingAlbums(
            LibrarySnapshotPlanner.accountKey(email), limit = 30,
        )

    suspend fun probeAlbum(
        email: String, password: String, id: String,
    ): AlbumSourceProbe {
        currentCoroutineContext().ensureActive()
        if (password.isBlank() || id.isBlank()) return AlbumSourceProbe(
            "MISSING_INPUT", false, false, 0,
        )
        val key = LibrarySnapshotPlanner.accountKey(email)
        val selected = database.remoteTrackDao().playableById(id, key)
            ?: return AlbumSourceProbe("NOT_FOUND", false, false, 0)
        if (!eligibleForSingleTagWrite(key, selected) ||
            !(selected.fileName.endsWith(".flac", true) ||
              selected.fileName.endsWith(".mp3", true))) {
            return AlbumSourceProbe("UNSUPPORTED", false, false, 0)
        }
        var reader: WebDavRangeReader? = null
        return try {
            val active = WebDavRangeReader(
                encodedPath = selected.encodedPath,
                fileSize = requireNotNull(selected.remoteSize),
                email = email,
                appPassword = password,
                etag = selected.remoteEtag,
                maxRequests = 6,
                maxDownloadedBytes = 6L * 65536L,
            )
            reader = active
            val result = active.use { source ->
                FastAudioTagReader().read(
                    selected.fileName, source.fileSize, source::readAt,
                )
            }
            val valid = result.code in setOf(
                FastTagCode.FLAC_VORBIS, FastTagCode.MP3_ID3V23,
                FastTagCode.MP3_ID3V24, FastTagCode.MP3_ID3V1,
                FastTagCode.NO_EMBEDDED_TAGS,
            )
            AlbumSourceProbe(
                result.code.name, valid,
                result.embeddedAlbum, active.stats().requests,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // No exception message, path, credentials or remote tag values.
            AlbumSourceProbe(
                "READ_ERROR", false, false, reader?.stats()?.requests ?: 0,
            )
        } finally {
            reader?.close()
        }
    }

    suspend fun scheduleOne(email: String, id: String): Boolean {
        if (id.isBlank()) return false
        val key = LibrarySnapshotPlanner.accountKey(email)
        return database.remoteTrackDao().requestTagRefresh(id, key) == 1
    }

    suspend fun scheduleAll(email: String): Int =
        database.remoteTrackDao().requestFullTagRefresh(
            LibrarySnapshotPlanner.accountKey(email),
        )

    suspend fun queuedCount(email: String): Int =
        database.remoteTrackDao().queuedTagRefreshCount(
            LibrarySnapshotPlanner.accountKey(email),
        )
}
