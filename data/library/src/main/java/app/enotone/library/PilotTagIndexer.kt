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
    val lastErrorStage: String = "NONE",
    val lastErrorReason: String = "NONE",
    val lastErrorHttpStatus: Int = 0,
    val permanentErrors: Int = 0,
)

/** Fixed, sanitized codes only: never include exception messages or remote metadata. */
internal data class PilotFailure(
    val code: String,
    val stage: String,
    val reason: String,
    val httpStatus: Int,
    val authRejected: Boolean = false,
)

internal object PilotFailures {
    fun classify(stage: String, stats: RangeReadStats?): PilotFailure {
        val issue = stats?.issue
        val httpStatus = stats?.httpStatus ?: 0
        val auth = issue == RangeReadIssue.HTTP_STATUS && httpStatus in listOf(401, 403)
        val code = when {
            auth -> "RETRY_AUTH"
            issue == RangeReadIssue.HTTP_STATUS && (
                httpStatus in 500..599 || httpStatus in listOf(408, 425, 429)
            ) -> "RETRY_HTTP"
            issue == RangeReadIssue.HTTP_STATUS -> "FAILED_HTTP"
            issue == RangeReadIssue.NETWORK || issue == RangeReadIssue.SHORT_BODY ||
                issue == RangeReadIssue.CHANGED_ETAG -> "RETRY_NETWORK"
            issue == RangeReadIssue.LIMIT -> "FAILED_BUDGET"
            issue != null -> "FAILED_RANGE"
            stage == "PARSER" -> "FAILED_PARSER"
            else -> "FAILED_PREPARATION"
        }
        return PilotFailure(
            code = code,
            stage = if (issue != null) "RANGE" else stage,
            reason = issue?.name ?: if (stage == "PARSER") "PARSER_FAILURE" else "PREPARATION_FAILURE",
            httpStatus = httpStatus,
            authRejected = auth,
        )
    }
}

data class PilotCheckpointSummary(
    val successful: Int,
    val retryableErrors: Int,
    val permanentErrors: Int,
    val notAttempted: Int,
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
    suspend fun offlineCheckpoint(email: String): PilotCheckpointSummary {
        val sourceKey = LibrarySnapshotPlanner.accountKey(email)
        val dao = database.remoteTrackDao()
        val retryable = dao.retryableFastScanCount(sourceKey)
        return PilotCheckpointSummary(
            successful = dao.finishedFastScanCount(sourceKey),
            retryableErrors = retryable,
            permanentErrors = dao.permanentFastScanCount(sourceKey),
            notAttempted = (dao.pendingFastScans(sourceKey).size - retryable).coerceAtLeast(0),
        )
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
        var permanentErrors = 0
        var lastFailure: PilotFailure? = null
        var gets = 0
        var bytes = 0L
        var stop = "COMPLETED"
        suspend fun report(): PilotIndexProgress {
            val remaining = dao.pendingFastScans(sourceKey).size
            return PilotIndexProgress(
                count, attempted, savedWithTags, completedWithoutTags,
                retryableErrors, staleWrites, gets, bytes, remaining, stop,
                lastFailure?.stage ?: "NONE",
                lastFailure?.reason ?: "NONE",
                lastFailure?.httpStatus ?: 0, permanentErrors)
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
            var stage = "RANGE"
            try {
                currentCoroutineContext().ensureActive()
                reader = WebDavRangeReader(
                    encodedPath = item.encodedPath, fileSize = item.remoteSize!!,
                    email = email, appPassword = password, etag = item.remoteEtag,
                    maxRequests = minOf(6, remainingGets),
                    maxDownloadedBytes = minOf(6L * 65536, remainingBytes),
                )
                reader.use { range ->
                    stage = "PARSER"
                    result = FastAudioTagReader().read(item.fileName, range.fileSize, range::readAt)
                    recordCode = requireNotNull(result).code.name
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Do not expose WebDAV exception messages or private metadata.
                val failure = PilotFailures.classify(stage, reader?.stats())
                lastFailure = failure
                authRejected = failure.authRejected
                recordCode = failure.code
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
                recordCode.startsWith("FAILED_") -> permanentErrors++
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
