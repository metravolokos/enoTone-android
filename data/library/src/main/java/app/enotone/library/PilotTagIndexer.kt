package app.enotone.library

import app.enotone.database.EnoToneDatabase
import app.enotone.database.RemoteTrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale

/**
 * User-triggered sequential indexing: 10/20-file pilots or a bounded resume
 * up to 100 candidates, with the SAME per-run ceilings on network and time.
 * Every result is checkpointed in Room before the next GET. Process death
 * cannot erase completed work. No background jobs or saved password here.
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
    /** A parsed error code is not a successful completed tag check. */
    fun fromParserResult(code: FastTagCode, stats: RangeReadStats?): PilotFailure? =
        when (code) {
            FastTagCode.INVALID_METADATA -> PilotFailure(
                "FAILED_METADATA", "PARSER", code.name, stats?.httpStatus ?: 0)
            FastTagCode.FAST_BUDGET_EXCEEDED -> PilotFailure(
                "FAILED_BUDGET", "PARSER", code.name, stats?.httpStatus ?: 0)
            else -> null
        }

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

internal object PilotBudget {
    const val MAX_BATCH_BYTES = 4L * 1024 * 1024
    const val MAX_BATCH_GETS = 60
    const val MAX_BATCH_NANOS = 180L * 1_000_000_000L
    private const val BLOCK = 65536L
    // The 100-file mode is a candidate ceiling, NEVER a promise of 100 GETs.
    // It shares the existing 60-request, 4-MiB, ~180-second hard/soft caps.
    const val RESUME_LIMIT = 100
    fun validCount(count: Int): Boolean =
        count == 10 || count == 20 || count == RESUME_LIMIT
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
                    // Retry entries may retain last-known-good tags during refresh.
                    (it.tagScanCode?.startsWith("RETRY_") == true ||
                        (it.title == null && it.artist == null &&
                            it.album == null && it.albumArtist == null)) &&
                    (it.fileName.endsWith(".mp3", true) || it.fileName.endsWith(".flac", true))
            }
            .sortedWith(compareBy<RemoteTrackEntity> { it.tagScanCode != null }
                .thenBy { it.id })
            .take(count).toList()
    }
}

class PilotTagIndexer(private val database: EnoToneDatabase) {
    suspend fun run(
        email: String,
        password: String,
        count: Int,
        onProgress: suspend (PilotIndexProgress) -> Unit = {},
        // Optional policy gate for managed sessions; old 10/20/100 pilots
        // retain their previously accepted behavior.
        beforeFile: suspend () -> String? = { null },
        stopOnRetryableFailure: Boolean = false,
        // Null keeps the legacy pilot behavior. Non-null restricts ALL GETs
        // to explicit local track IDs, even if unrelated scans are pending.
        targetIds: Set<String>? = null,
        onFileCompleted: suspend (String) -> Unit = {},
        // True means the same-version Room row was updated with a terminal
        // result. Transient/failed/stale attempts must stay eligible to retry.
        onFileOutcome: suspend (String, Boolean) -> Unit = { _, _ -> },
    ): PilotIndexProgress {
        require(PilotBudget.validCount(count))
        require(password.isNotBlank())
        val sourceKey = LibrarySnapshotPlanner.accountKey(email)
        val dao = database.remoteTrackDao()
        val pending = dao.pendingFastScans(sourceKey)
        val candidates = PilotBudget.select(
            sourceKey,
            if (targetIds == null) pending else pending.filter { it.id in targetIds },
            count,
        )
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
            // Check network policy before every new file; no HTTP after a
            // network change has been noticed at the file boundary.
            val policyStop = beforeFile()
            if (policyStop != null) {
                stop = policyStop
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
                    result = FastAudioTagReader().read(
                        item.fileName, range.fileSize, range::readAt, range::readCachedAt,
                    )
                    val parsed = requireNotNull(result)
                    recordCode = parsed.code.name
                    PilotFailures.fromParserResult(parsed.code, range.stats())?.let { failure ->
                        lastFailure = failure
                        recordCode = failure.code
                    }
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
                year = tags?.year, trackNumber = tags?.trackNumber,
                discNumber = tags?.discNumber,
                genre = tags?.genre,
                durationMs = result?.durationMs,
            ) == 1
            if (saved) TrackSearchIndex(database).refreshById(sourceKey, item.id)
            attempted++
            // Opaque local ID only. Allows an album refresh button to advance
            // past the exact files handled, not guess from aggregate counters.
            onFileCompleted(item.id)
            onFileOutcome(
                item.id,
                saved && !recordCode.startsWith("RETRY_") &&
                    !recordCode.startsWith("FAILED_"),
            )
            when {
                !saved -> staleWrites++
                recordCode.startsWith("RETRY_") -> retryableErrors++
                recordCode.startsWith("FAILED_") -> permanentErrors++
                tags != null -> savedWithTags++
                else -> completedWithoutTags++
            }
            onProgress(report())
            // Managed sessions must not hammer a throttled/failed server.
            // Retryable items stay in Room and can be retried manually later.
            if (stopOnRetryableFailure && recordCode.startsWith("RETRY_")) {
                stop = "RETRY_BACKOFF"
                break
            }
            // Stop if the cumulative HTTP budget is exhausted. No automatic retry.
            if (!PilotBudget.mayStartFile(gets, bytes, count)) {
                stop = "NETWORK_BUDGET"
                break
            }
        }
        return report()
    }
}
