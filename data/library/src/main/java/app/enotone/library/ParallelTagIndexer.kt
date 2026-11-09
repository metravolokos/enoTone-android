package app.enotone.library

import app.enotone.database.EnoToneDatabase
import app.enotone.database.RemoteTrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.join
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Four HTTP readers, never four database writers. All four workers share one
 * atomic HTTP request/byte budget. No personal file/credential data in errors.
 */
internal object ParallelTagPolicy {
    const val WORKERS = 4
    const val MAX_RETRIES = 2
    const val MAX_CONSECUTIVE_TRANSIENT_FAILURES = 4
    const val MAX_FINAL_THROTTLES = 2

    fun retryDelayMs(attemptIndex: Int, throttled: Boolean): Long =
        if (throttled) (if (attemptIndex == 0) 8_000L else 16_000L)
        else (if (attemptIndex == 0) 1_000L else 3_000L)
}

/** Fixed, non-sensitive pipeline stage; no exception message may reach the UI. */
class TagBatchStageException(val phase: String, cause: Exception) :
    IllegalStateException("TAG_BATCH_STAGE_$phase", cause)

private suspend fun <T> runStage(phase: String, block: suspend () -> T): T =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        if (failure is TagBatchStageException) throw failure
        throw TagBatchStageException(phase, failure)
    }

private data class ParallelTagAttempt(
    val code: String,
    val tags: AudioTagFields?,
    val stats: RangeReadStats,
    val authRejected: Boolean,
    val failure: PilotFailure? = null,
    val exhaustedGlobalBudget: Boolean = false,
) {
    val retryable: Boolean get() = code.startsWith("RETRY_")
    val throttled: Boolean get() =
        stats.issue == RangeReadIssue.HTTP_STATUS && stats.httpStatus == 429
}

private data class CompletedTagFile(
    val item: RemoteTrackEntity,
    val attempts: List<ParallelTagAttempt>,
    val final: ParallelTagAttempt?,
)

/**
 * The per-file downloads run concurrently on Dispatchers.IO.
 *
 * All Room CAS writes, progress counters, pending queries and notification
 * callbacks run through ONE collector coroutine. This avoids concurrent Room
 * write/observe operations and serializes progress reports on Huawei.
 *
 * A worker never calls a DAO or the UI. A failed collector cancels the whole
 * scope and reports a fixed stage, rather than a stale RUNNING state.
 */
class ParallelTagIndexer(private val database: EnoToneDatabase) {
    suspend fun run(
        email: String,
        password: String,
        count: Int,
        onProgress: suspend (PilotIndexProgress) -> Unit = {},
        beforeFile: suspend () -> String? = { null },
    ): PilotIndexProgress = coroutineScope {
        require(PilotBudget.validCount(count))
        require(email.isNotBlank() && password.isNotBlank())

        val sourceKey = LibrarySnapshotPlanner.accountKey(email)
        val dao = database.remoteTrackDao()
        val candidates = runStage("DB_SELECT") {
            PilotBudget.select(sourceKey, dao.pendingFastScans(sourceKey), count)
        }
        val shared = SharedRangeBudget(
            PilotBudget.requestCap(count), PilotBudget.byteCap(count),
        )
        val nextCandidate = AtomicInteger(0)
        val stop = AtomicReference<String?>(null)
        val cooldownUntil = AtomicLong(0L)
        val started = System.nanoTime()

        // Channel capacity bounded to the active worker count. Slow Room writes
        // throttle upstream workers instead of building an unbounded RAM queue.
        val completed = Channel<CompletedTagFile>(ParallelTagPolicy.WORKERS)
        var processed = 0
        var tagged = 0
        var withoutTags = 0
        var retryableErrors = 0
        var permanentErrors = 0
        var staleWrites = 0
        var getRequests = 0
        var downloadedBytes = 0L
        var consecutiveFailures = 0
        var finalThrottles = 0
        var lastFailure: PilotFailure? = null

        val collector = launch(Dispatchers.IO) {
            for (file in completed) {
                // One writer owns all DB updates. Every completed success is
                // checkpointed in Room before its public progress event.
                val attempt = file.final
                val committed = if (attempt == null) null else runStage("DB_WRITE") {
                    dao.recordFastScanIfUnchanged(
                        id = file.item.id,
                        sourceKey = sourceKey,
                        path = file.item.encodedPath,
                        etag = file.item.remoteEtag,
                        size = file.item.remoteSize,
                        modified = file.item.remoteModified,
                        code = attempt.code,
                        title = attempt.tags?.title,
                        artist = attempt.tags?.artist,
                        album = attempt.tags?.album,
                        albumArtist = attempt.tags?.albumArtist,
                    ) == 1
                }
                getRequests += file.attempts.sumOf { it.stats.requests }
                downloadedBytes += file.attempts.sumOf { it.stats.downloadedBytes }
                if (committed != null) {
                    processed++
                    if (!committed) staleWrites++
                    else when {
                        attempt!!.retryable -> retryableErrors++
                        attempt.code.startsWith("FAILED_") -> permanentErrors++
                        attempt.tags != null -> tagged++
                        else -> withoutTags++
                    }
                    lastFailure = attempt!!.failure ?: lastFailure

                    if (committed) {
                        if (attempt.retryable) {
                            consecutiveFailures++
                            if (consecutiveFailures >=
                                ParallelTagPolicy.MAX_CONSECUTIVE_TRANSIENT_FAILURES) {
                                stop.compareAndSet(null, "RETRY_BACKOFF")
                            }
                            if (attempt.throttled &&
                                ++finalThrottles >= ParallelTagPolicy.MAX_FINAL_THROTTLES) {
                                stop.compareAndSet(null, "RETRY_BACKOFF")
                            }
                        } else {
                            consecutiveFailures = 0
                        }
                    }
                }
                val remaining = runStage("DB_COUNT") {
                    dao.pendingFastScans(sourceKey).size
                }
                val report = PilotIndexProgress(
                    requested = count,
                    attempted = processed,
                    savedWithTags = tagged,
                    completedWithoutTags = withoutTags,
                    retryableErrors = retryableErrors,
                    staleWrites = staleWrites,
                    getRequests = getRequests,
                    downloadedBytes = downloadedBytes,
                    remaining = remaining,
                    stopCode = stop.get() ?: "RUNNING",
                    lastErrorStage = lastFailure?.stage ?: "NONE",
                    lastErrorReason = lastFailure?.reason ?: "NONE",
                    lastErrorHttpStatus = lastFailure?.httpStatus ?: 0,
                    permanentErrors = permanentErrors,
                )
                runStage("PROGRESS") { onProgress(report) }
            }
        }

        val workers = (0 until ParallelTagPolicy.WORKERS).map {
            launch(Dispatchers.IO) {
                while (currentCoroutineContext().isActive) {
                    currentCoroutineContext().ensureActive()
                    if (stop.get() != null) break
                    if (System.nanoTime() - started >= PilotBudget.MAX_BATCH_NANOS) {
                        stop.compareAndSet(null, "TIME_BUDGET")
                        break
                    }
                    if (!shared.canStartFile()) {
                        stop.compareAndSet(null, "NETWORK_BUDGET")
                        break
                    }
                    val policyStop = runStage("POLICY") { beforeFile() }
                    if (policyStop != null) {
                        stop.compareAndSet(null, policyStop)
                        break
                    }
                    val i = nextCandidate.getAndIncrement()
                    if (i >= candidates.size) break
                    val item = candidates[i]
                    val attempts = mutableListOf<ParallelTagAttempt>()
                    var final: ParallelTagAttempt? = null

                    for (retry in 0..ParallelTagPolicy.MAX_RETRIES) {
                        currentCoroutineContext().ensureActive()
                        if (stop.get() != null) break
                        if (System.nanoTime() - started >= PilotBudget.MAX_BATCH_NANOS) {
                            stop.compareAndSet(null, "TIME_BUDGET")
                            break
                        }
                        if (!shared.canStartFile()) {
                            stop.compareAndSet(null, "NETWORK_BUDGET")
                            break
                        }
                        // Also honor global 429 cooldown before the first request
                        // for a NEW file; other workers must not bypass it.
                        val waitMs = (cooldownUntil.get() -
                            System.nanoTime()) / 1_000_000L
                        if (waitMs > 0L) delay(waitMs)
                        if (stop.get() != null) break

                        val attempt = readFile(item, email, password, shared)
                        attempts.add(attempt)
                        if (attempt.exhaustedGlobalBudget) {
                            stop.compareAndSet(null, "NETWORK_BUDGET")
                            break
                        }
                        if (attempt.authRejected) {
                            stop.set("AUTH_REJECTED")
                            break
                        }
                        final = attempt
                        if (!attempt.retryable || retry == ParallelTagPolicy.MAX_RETRIES) break
                        val pause = ParallelTagPolicy.retryDelayMs(retry, attempt.throttled)
                        if (attempt.throttled) {
                            val until = System.nanoTime() + pause * 1_000_000L
                            cooldownUntil.updateAndGet { maxOf(it, until) }
                        }
                        delay(pause)
                    }
                    // The collector owns all file checkpoints and progress.
                    // Also count GETs spent by interrupted/failed attempts.
                    completed.send(CompletedTagFile(item, attempts.toList(),
                        if (stop.get() == "AUTH_REJECTED") null else final))
                }
            }
        }

        workers.joinAll()
        completed.close()
        collector.join()

        val remaining = runStage("DB_FINAL") { dao.pendingFastScans(sourceKey).size }
        val endCode = stop.get() ?: when {
            candidates.isEmpty() || remaining == 0 -> "COMPLETED"
            nextCandidate.get() >= candidates.size -> "COMPLETED"
            else -> "NETWORK_BUDGET"
        }
        PilotIndexProgress(
            requested = count,
            attempted = processed,
            savedWithTags = tagged,
            completedWithoutTags = withoutTags,
            retryableErrors = retryableErrors,
            staleWrites = staleWrites,
            getRequests = getRequests,
            downloadedBytes = downloadedBytes,
            remaining = remaining,
            stopCode = endCode,
            lastErrorStage = lastFailure?.stage ?: "NONE",
            lastErrorReason = lastFailure?.reason ?: "NONE",
            lastErrorHttpStatus = lastFailure?.httpStatus ?: 0,
            permanentErrors = permanentErrors,
        )
    }

    private fun readFile(
        item: RemoteTrackEntity,
        email: String,
        password: String,
        budget: SharedRangeBudget,
    ): ParallelTagAttempt {
        var reader: WebDavRangeReader? = null
        var failure: PilotFailure? = null
        var tags: AudioTagFields? = null
        var code = "RETRY_ERROR"
        var auth = false
        var stage = "RANGE"
        try {
            reader = WebDavRangeReader(
                encodedPath = item.encodedPath,
                fileSize = requireNotNull(item.remoteSize),
                email = email,
                appPassword = password,
                etag = item.remoteEtag,
                maxRequests = 6,
                maxDownloadedBytes = 6L * 65536L,
                sharedBudget = budget,
            )
            reader.use { range ->
                stage = "PARSER"
                val parsed = FastAudioTagReader().read(
                    item.fileName, range.fileSize, range::readAt)
                code = parsed.code.name
                tags = parsed.tags
                PilotFailures.fromParserResult(parsed.code, range.stats())?.let {
                    failure = it
                    code = it.code
                    tags = null
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failure = PilotFailures.classify(stage, reader?.stats())
            code = requireNotNull(failure).code
            auth = requireNotNull(failure).authRejected
        } finally {
            reader?.close()
        }
        val stats = reader?.stats() ?: RangeReadStats(0, 0)
        return ParallelTagAttempt(
            code = code, tags = tags, stats = stats, authRejected = auth,
            failure = failure,
            exhaustedGlobalBudget = stats.issue == RangeReadIssue.LIMIT &&
                !budget.canStartFile(),
        )
    }
}
