package app.enotone.library

import app.enotone.database.EnoToneDatabase
import app.enotone.database.RemoteTrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale

/**
 * Deliberately small, user-triggered, sequential pilot: 10 or 20 files only.
 * Every result is checkpointed in Room before the next GET; process death
 * therefore cannot erase completed work. No background job or saved password.
 */
data class PilotIndexProgress(
    val requested: Int,
    val attempted: Int,
    val savedWithTags: Int,
    val completedWithoutTags: Int,
    val retryableErrors: Int,
    val staleWrites: Int,
    val getRequests: Int,
    val downloadedBytes: Long,
    val remaining: Int,
    val stopCode: String,
)

internal object PilotBudget {
    const val MAX_BATCH_BYTES = 4L * 1024 * 1024
    const val MAX_BATCH_GETS = 60
    const val MAX_BATCH_NANOS = 180L * 1_000_000_000L
    private const val BLOCK = 65536L
    fun validCount(count: Int): Boolean = count == 10 || count == 20
    fun requestCap(count: Int) = minOf(MAX_BATCH_GETS, count * 3)
    fun byteCap(count: Int) = minOf(MAX_BATCH_BYTES, count * 256L * 1024)
    fun mayStartFile(usedGets: Int, usedBytes: Long, count: Int): Boolean =
        usedGets < requestCap(count) && byteCap(count) - usedBytes >= BLOCK

    /** Prefer never-attempted files; retryable failures come last. Stable order. */
    fun select(sourceKey: String, rows: List<RemoteTrackEntity>, count: Int): List<RemoteTrackEntity> {
        require(validCount(count))
        return rows.asSequence()
            .filter { eligibleForSingleTagWrite(sourceKey, it) }
            .filter {
                (it.tagScanCode?.startsWith("RETRY_") != false) &&
                    it.title == null && it.artist == null &&
                    it.album == null && it.albumArtist == null &&
                    (it.fileName.endsWith(".mp3", true) || it.fileName.endsWith(".flac", true))
            }
            .sortedWith(compareBy<RemoteTrackEntity> { it.tagScanCode != null }
                .thenBy { it.id })
            .take(count).toList()
    }
}

class PilotTagIndexer(private val database: EnoToneDatabase) {
    /** Uses only local SQLite and the account's SHA key; no password or HTTP. */
    suspend fun offlineCheckpoint(email: String): Pair<Int, Int> {
        val sourceKey = LibrarySnapshotPlanner.accountKey(email)
        val dao = database.remoteTrackDao()
        return dao.finishedFastScanCount(sourceKey) to
            dao.pendingFastScans(sourceKey).size
    }

    suspend fun run(
        email: String,
        password: String,
        count: Int,
        onProgress: suspend (PilotIndexProgress) -> Unit = {},
    ): PilotIndexProgress {
        require(PilotBudget.validCount(count))
        require(password.isNotBlank())
        val sourceKey = LibrarySnapshotPlanner.accountKey(email)
        val dao = database.remoteTrackDao()
        val candidates = PilotBudget.select(sourceKey, dao.pendingFastScans(sourceKey), count)
        val started = System.nanoTime()
        var attempted = 0
        var savedWithTags = 0
        var completedWithoutTags = 0
        var retryableErrors = 0
        var staleWrites = 0
        var gets = 0
        var bytes = 0L
        var stop = "COMPLETED"
        suspend fun report(): PilotIndexProgress {
            val remaining = dao.pendingFastScans(sourceKey).size
            return PilotIndexProgress(
                count, attempted, savedWithTags, completedWithoutTags,
                retryableErrors, staleWrites, gets, bytes, remaining, stop)
        }
        for (item in candidates) {
            currentCoroutineContext().ensureActive()
            if (System.nanoTime() - started >= PilotBudget.MAX_BATCH_NANOS) {
                stop = "TIME_BUDGET"
                break
            }
            if (!PilotBudget.mayStartFile(gets, bytes, count)) {
                stop = "NETWORK_BUDGET"
                break
            }
            val remainingGets = PilotBudget.requestCap(count) - gets
            val remainingBytes = PilotBudget.byteCap(count) - bytes
            var reader: WebDavRangeReader? = null
            var result: FastTagRead? = null
            var recordCode = "RETRY_ERROR"
            var authRejected = false
            try {
                currentCoroutineContext().ensureActive()
                reader = WebDavRangeReader(
                    encodedPath = item.encodedPath, fileSize = item.remoteSize!!,
                    email = email, appPassword = password, etag = item.remoteEtag,
                    maxRequests = minOf(6, remainingGets),
                    maxDownloadedBytes = minOf(6L * 65536, remainingBytes),
                )
                reader.use { range ->
                    result = FastAudioTagReader().read(item.fileName, range.fileSize, range::readAt)
                    recordCode = requireNotNull(result).code.name
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Do not expose WebDAV exception messages or private metadata.
                val stats = reader?.stats()
                authRejected = stats?.issue == RangeReadIssue.HTTP_STATUS &&
                    stats.httpStatus in listOf(401, 403)
                recordCode = if (authRejected) "RETRY_AUTH" else "RETRY_NETWORK"
            } finally {
                val stats = reader?.stats()
                gets += stats?.requests ?: 0
                bytes += stats?.downloadedBytes ?: 0
                reader?.close()
            }
            currentCoroutineContext().ensureActive()
            if (authRejected) {
                stop = "AUTH_REJECTED"
                break
            }
            val tags = result?.tags
            // One atomic row update, including the result checkpoint.
            val saved = dao.recordFastScanIfUnchanged(
                id = item.id, sourceKey = sourceKey, path = item.encodedPath,
                etag = item.remoteEtag, size = item.remoteSize,
                modified = item.remoteModified, code = recordCode,
                title = tags?.title, artist = tags?.artist,
                album = tags?.album, albumArtist = tags?.albumArtist,
            ) == 1
            attempted++
            when {
                !saved -> staleWrites++
                recordCode.startsWith("RETRY_") -> retryableErrors++
                tags != null -> savedWithTags++
                else -> completedWithoutTags++
            }
            onProgress(report())
            // Stop if the cumulative HTTP budget is exhausted. No automatic retry.
            if (!PilotBudget.mayStartFile(gets, bytes, count)) {
                stop = "NETWORK_BUDGET"
                break
            }
        }
        return report()
    }
}
