package app.enotone.library

import app.enotone.database.EnoToneDatabase
import app.enotone.database.RemoteTrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * First measured parallel phase: four files at a time, one HTTP request at a
 * time PER FILE. Every request, including a retry, must reserve from the SAME
 * 60-GET / 4-MiB budget before opening a connection.
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

/** No personal fields may be published in an index progress snapshot. */
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

/**
 * Only managed background sessions use this implementation. Original pilot
 * buttons remain sequential and unchanged. Jobs and Range readers are not
 * shared between files; a global request budget and Room CAS are shared.
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
        val candidates = PilotBudget.select(sourceKey, dao.pendingFastScans(sourceKey), count)
        val shared = SharedRangeBudget(
            PilotBudget.requestCap(count), PilotBudget.byteCap(count),
        )
        val nextCandidate = AtomicInteger(0)
        val stop = AtomicReference<String?>(null)
        val consecutiveFailures = AtomicInteger(0)
        val finalThrottles = AtomicInteger(0)
        val cooldownUntil = AtomicLong(0L)
        val started = System.nanoTime()
        val progressMutex = Mutex()

        var processed = 0
        var tagged = 0
        var withoutTags = 0
        var retryableErrors = 0
        var permanentErrors = 0
        var staleWrites = 0
        var getRequests = 0
        var downloadedBytes = 0L
        var lastFailure: PilotFailure? = null

        suspend fun checkpoint(attempts: List<ParallelTagAttempt>,
                               outcome: ParallelTagAttempt?,
                               committed: Boolean?) {
            // Each file is checkpointed independently. In-flight jobs may finish
            // after another worker has exhausted the common network budget.
            val remaining = dao.pendingFastScans(sourceKey).size
            progressMutex.withLock {
                getRequests += attempts.sumOf { it.stats.requests }
                downloadedBytes += attempts.sumOf { it.stats.downloadedBytes }
                if (outcome != null && committed != null) {
                    processed++
                    if (!committed) staleWrites++
                    else when {
                        outcome.retryable -> retryableErrors++
                        outcome.code.startsWith("FAILED_") -> permanentErrors++
                        outcome.tags != null -> tagged++
                        else -> withoutTags++
                    }
                    lastFailure = outcome.failure ?: lastFailure
                }
                onProgress(PilotIndexProgress(
                    requested = count, attempted = processed,
                    savedWithTags = tagged, completedWithoutTags = withoutTags,
                    retryableErrors = retryableErrors, staleWrites = staleWrites,
                    getRequests = getRequests, downloadedBytes = downloadedBytes,
                    remaining = remaining, stopCode = stop.get() ?: "RUNNING",
                    lastErrorStage = lastFailure?.stage ?: "NONE",
                    lastErrorReason = lastFailure?.reason ?: "NONE",
                    lastErrorHttpStatus = lastFailure?.httpStatus ?: 0,
                    permanentErrors = permanentErrors,
                ))
            }
        }

        val workers = (0 until ParallelTagPolicy.WORKERS).map {
            launch(Dispatchers.IO) {
                while (true) {
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
                    val policyStop = beforeFile()
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
                        val waitMs = (cooldownUntil.get() -
                            System.nanoTime()) / 1_000_000L
                        if (waitMs > 0L) delay(waitMs)
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

                    if (final == null || stop.get() == "AUTH_REJECTED") {
                        checkpoint(attempts, null, null)
                        continue
                    }
                    val committed = dao.recordFastScanIfUnchanged(
                        id = item.id, sourceKey = sourceKey, path = item.encodedPath,
                        etag = item.remoteEtag, size = item.remoteSize,
                        modified = item.remoteModified, code = final.code,
                        title = final.tags?.title, artist = final.tags?.artist,
                        album = final.tags?.album, albumArtist = final.tags?.albumArtist,
                    ) == 1
                    if (committed) {
                        if (final.retryable) {
                            if (consecutiveFailures.incrementAndGet() >=
                                ParallelTagPolicy.MAX_CONSECUTIVE_TRANSIENT_FAILURES) {
                                stop.compareAndSet(null, "RETRY_BACKOFF")
                            }
                            if (final.throttled &&
                                finalThrottles.incrementAndGet() >=
                                ParallelTagPolicy.MAX_FINAL_THROTTLES) {
                                stop.compareAndSet(null, "RETRY_BACKOFF")
                            }
                        } else {
                            consecutiveFailures.set(0)
                        }
                    }
                    checkpoint(attempts, final, committed)
                }
            }
        }

        workers.joinAll()
        val remaining = dao.pendingFastScans(sourceKey).size
        val endCode = stop.get() ?: when {
            candidates.isEmpty() || remaining == 0 -> "COMPLETED"
            nextCandidate.get() >= candidates.size -> "COMPLETED"
            else -> "NETWORK_BUDGET"
        }
        PilotIndexProgress(
            requested = count, attempted = processed,
            savedWithTags = tagged, completedWithoutTags = withoutTags,
            retryableErrors = retryableErrors, staleWrites = staleWrites,
            getRequests = getRequests, downloadedBytes = downloadedBytes,
            remaining = remaining, stopCode = endCode,
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
