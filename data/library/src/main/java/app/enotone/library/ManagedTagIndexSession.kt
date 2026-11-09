package app.enotone.library

import app.enotone.database.EnoToneDatabase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * One explicit user-controlled session. Four sequential batches, with up to
 * four parallel per-file readers INSIDE each batch. All requests and retries
 * share the same 60 GET / 4 MiB cap; Room checkpoints each completed file.
 * The Android foreground service owns this coroutine and credentials in RAM.
 */
object ManagedIndexLimits {
    const val MAX_BATCHES = 4
    const val MAX_TOTAL_GETS = MAX_BATCHES * PilotBudget.MAX_BATCH_GETS
    const val MAX_TOTAL_BYTES = MAX_BATCHES * PilotBudget.MAX_BATCH_BYTES
    const val BETWEEN_BATCH_DELAY_MS = 1500L

    fun underGlobalCaps(batches: Int, gets: Int, bytes: Long): Boolean =
        batches in 0 until MAX_BATCHES && gets <= MAX_TOTAL_GETS &&
            bytes <= MAX_TOTAL_BYTES
}

data class ManagedIndexProgress(
    val completedBatches: Int,
    val attempted: Int,
    val savedWithTags: Int,
    val completedWithoutTags: Int,
    val retryableErrors: Int,
    val permanentErrors: Int,
    val staleWrites: Int,
    val getRequests: Int,
    val downloadedBytes: Long,
    val remaining: Int,
    val stopCode: String,
    val lastErrorStage: String = "NONE",
    val lastErrorReason: String = "NONE",
    val lastErrorHttpStatus: Int = 0,
)

/**
 * Injected per-batch runner keeps aggregate accounting testable without
 * accessing real WebDAV data or depending on a device-specific runtime.
 */
class ManagedTagIndexSession(
    private val runBatch: suspend (
        email: String,
        password: String,
        beforeFile: suspend () -> String?,
        onProgress: suspend (PilotIndexProgress) -> Unit,
    ) -> PilotIndexProgress,
) {
    constructor(database: EnoToneDatabase) : this(
        { email, password, gate, progress ->
            ParallelTagIndexer(database).run(
                email, password, PilotBudget.RESUME_LIMIT,
                onProgress = progress,
                beforeFile = gate,
            )
        }
    )

    /**
     * Legacy predicate is retained for compatibility with tests/callers. The
     * foreground service supplies { true }: Wi-Fi/mobile transport does not
     * change the network policy.
     */
    suspend fun run(
        email: String,
        password: String,
        networkAllowed: () -> Boolean,
        onProgress: suspend (ManagedIndexProgress) -> Unit = {},
    ): ManagedIndexProgress {
        require(email.isNotBlank() && password.isNotBlank())
        var batches = 0
        var attempted = 0
        var tagged = 0
        var untagged = 0
        var retries = 0
        var permanent = 0
        var stale = 0
        var gets = 0
        var bytes = 0L
        var remaining = 0
        var stop = "SESSION_LIMIT"
        var lastStage = "NONE"
        var lastReason = "NONE"
        var lastHttp = 0

        fun snapshot(partial: PilotIndexProgress? = null, code: String = stop) =
            ManagedIndexProgress(
                completedBatches = batches,
                attempted = attempted + (partial?.attempted ?: 0),
                savedWithTags = tagged + (partial?.savedWithTags ?: 0),
                completedWithoutTags = untagged + (partial?.completedWithoutTags ?: 0),
                retryableErrors = retries + (partial?.retryableErrors ?: 0),
                permanentErrors = permanent + (partial?.permanentErrors ?: 0),
                staleWrites = stale + (partial?.staleWrites ?: 0),
                getRequests = gets + (partial?.getRequests ?: 0),
                downloadedBytes = bytes + (partial?.downloadedBytes ?: 0L),
                remaining = partial?.remaining ?: remaining,
                stopCode = code,
                lastErrorStage = partial?.lastErrorStage?.takeIf { it != "NONE" } ?: lastStage,
                lastErrorReason = partial?.lastErrorReason?.takeIf { it != "NONE" } ?: lastReason,
                lastErrorHttpStatus = if (partial?.lastErrorStage != null &&
                    partial.lastErrorStage != "NONE") partial.lastErrorHttpStatus else lastHttp,
            )

        while (ManagedIndexLimits.underGlobalCaps(batches, gets, bytes)) {
            currentCoroutineContext().ensureActive()
            if (batches > 0) delay(ManagedIndexLimits.BETWEEN_BATCH_DELAY_MS)
            if (!networkAllowed()) {
                stop = "NETWORK_POLICY"
                break
            }
            val result = runBatch(
                email, password,
                { if (networkAllowed()) null else "NETWORK_POLICY" },
                { progress ->
                    onProgress(snapshot(progress, "RUNNING"))
                },
            )
            // The batch owns a separate immutable 60 GET / 4 MiB limit.
            // We still fail closed if a future implementation violates it.
            check(result.getRequests in 0..PilotBudget.MAX_BATCH_GETS &&
                result.downloadedBytes in 0L..PilotBudget.MAX_BATCH_BYTES) {
                "Unexpected batch budget"
            }
            attempted += result.attempted
            tagged += result.savedWithTags
            untagged += result.completedWithoutTags
            retries += result.retryableErrors
            permanent += result.permanentErrors
            stale += result.staleWrites
            gets += result.getRequests
            bytes += result.downloadedBytes
            remaining = result.remaining
            if (result.lastErrorStage != "NONE") {
                lastStage = result.lastErrorStage
                lastReason = result.lastErrorReason
                lastHttp = result.lastErrorHttpStatus
            }
            batches++
            stop = when {
                result.stopCode in setOf("AUTH_REJECTED", "RETRY_BACKOFF", "NETWORK_POLICY") ->
                    result.stopCode
                result.attempted == 0 || remaining == 0 ->
                    "NO_ELIGIBLE_FILES"
                result.stopCode !in setOf("COMPLETED", "NETWORK_BUDGET", "TIME_BUDGET") ->
                    result.stopCode
                batches >= ManagedIndexLimits.MAX_BATCHES ->
                    "SESSION_LIMIT"
                else -> "RUNNING"
            }
            onProgress(snapshot())
            if (stop != "RUNNING") break
        }
        return snapshot(code = stop)
    }
}
