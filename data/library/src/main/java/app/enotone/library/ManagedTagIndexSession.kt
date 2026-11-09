package app.enotone.library

import app.enotone.database.EnoToneDatabase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * BOUNDED keeps the approved four-batch smoke-test behavior. CONTINUOUS
 * chains the same capped four-reader batches until all eligible files are
 * indexed or a safety/time/network boundary is reached. A user explicitly
 * chooses the mode; there is no auto-start at boot/process restart.
 */
enum class ManagedIndexMode { BOUNDED, CONTINUOUS }

object ManagedIndexLimits {
    const val MAX_BATCHES = 4
    const val MAX_TOTAL_GETS = MAX_BATCHES * PilotBudget.MAX_BATCH_GETS
    const val MAX_TOTAL_BYTES = MAX_BATCHES * PilotBudget.MAX_BATCH_BYTES
    const val BETWEEN_BATCH_DELAY_MS = 1500L
    // 80 * 60 GET covers ~3,274 remaining tracks at the observed ~1.16 GET
    // per track, with a small margin. A strict four-hour wall cap is still
    // below Android 15's six-hour dataSync foreground-service quota.
    const val MAX_CONTINUOUS_BATCHES = 80
    const val MAX_CONTINUOUS_GETS = MAX_CONTINUOUS_BATCHES * PilotBudget.MAX_BATCH_GETS
    const val MAX_CONTINUOUS_BYTES = MAX_CONTINUOUS_BATCHES * PilotBudget.MAX_BATCH_BYTES
    const val MAX_CONTINUOUS_NANOS = 4L * 60L * 60L * 1_000_000_000L
    const val MAX_BACKOFF_CYCLES = 3

    fun maxBatches(mode: ManagedIndexMode): Int =
        if (mode == ManagedIndexMode.CONTINUOUS) MAX_CONTINUOUS_BATCHES else MAX_BATCHES

    fun underGlobalCaps(
        batches: Int, gets: Int, bytes: Long,
        mode: ManagedIndexMode = ManagedIndexMode.BOUNDED,
    ): Boolean {
        val limit = maxBatches(mode)
        return batches in 0 until limit &&
            gets <= limit * PilotBudget.MAX_BATCH_GETS &&
            bytes <= limit * PilotBudget.MAX_BATCH_BYTES
    }

    fun backoffMs(cycle: Int): Long {
        require(cycle in 1 until MAX_BACKOFF_CYCLES)
        return if (cycle == 1) 30_000L else 60_000L
    }
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
    private val waitBetweenBatches: suspend (Long) -> Unit = { delay(it) },
    private val nowNanos: () -> Long = System::nanoTime,
    private val runBatch: suspend (
        email: String,
        password: String,
        beforeFile: suspend () -> String?,
        onProgress: suspend (PilotIndexProgress) -> Unit,
    ) -> PilotIndexProgress,
) {
    constructor(database: EnoToneDatabase) : this(
        runBatch = { email, password, gate, progress ->
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
        mode: ManagedIndexMode = ManagedIndexMode.BOUNDED,
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
        val sessionStarted = nowNanos()
        var backoffCycles = 0
        var consecutiveEmptyBatches = 0
        fun timedOut(): Boolean =
            mode == ManagedIndexMode.CONTINUOUS &&
                nowNanos() - sessionStarted >= ManagedIndexLimits.MAX_CONTINUOUS_NANOS

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

        while (ManagedIndexLimits.underGlobalCaps(batches, gets, bytes, mode)) {
            currentCoroutineContext().ensureActive()
            if (timedOut()) {
                stop = "SESSION_TIME_BUDGET"
                break
            }
            if (batches > 0) {
                waitBetweenBatches(ManagedIndexLimits.BETWEEN_BATCH_DELAY_MS)
            }
            if (!networkAllowed()) {
                stop = "NETWORK_POLICY"
                break
            }
            // The clock guard is checked both between batches and at file
            // boundaries. Wi-Fi/mobile transport is never inspected here.
            val result = runBatch(
                email, password,
                { when {
                    timedOut() -> "SESSION_TIME_BUDGET"
                    !networkAllowed() -> "NETWORK_POLICY"
                    else -> null
                } },
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
            consecutiveEmptyBatches =
                if (result.attempted == 0 && result.getRequests > 0) consecutiveEmptyBatches + 1
                else 0
            if (mode == ManagedIndexMode.CONTINUOUS &&
                result.stopCode == "RETRY_BACKOFF") backoffCycles++
            else if (result.savedWithTags > 0) backoffCycles = 0
            stop = when {
                result.stopCode in setOf("AUTH_REJECTED", "NETWORK_POLICY",
                    "SESSION_TIME_BUDGET") -> result.stopCode
                remaining == 0 || (result.attempted == 0 && result.getRequests == 0) ->
                    "NO_ELIGIBLE_FILES"
                consecutiveEmptyBatches >= 3 -> "NO_PROGRESS"
                result.stopCode == "RETRY_BACKOFF" -> when {
                    mode == ManagedIndexMode.BOUNDED ||
                        backoffCycles >= ManagedIndexLimits.MAX_BACKOFF_CYCLES -> "RETRY_BACKOFF"
                    else -> "COOLDOWN"
                }
                result.stopCode !in setOf("COMPLETED", "NETWORK_BUDGET", "TIME_BUDGET") ->
                    result.stopCode
                batches >= ManagedIndexLimits.maxBatches(mode) ->
                    if (mode == ManagedIndexMode.CONTINUOUS) "SESSION_BATCH_LIMIT"
                    else "SESSION_LIMIT"
                else -> "RUNNING"
            }
            onProgress(snapshot())
            if (stop == "COOLDOWN") {
                // Throttling is server-wide, not just a single file. Back off
                // before starting a fresh bounded batch; cancellation works.
                waitBetweenBatches(ManagedIndexLimits.backoffMs(backoffCycles))
                stop = "RUNNING"
            } else if (stop != "RUNNING") break
        }
        if (stop == "RUNNING" &&
            !ManagedIndexLimits.underGlobalCaps(batches, gets, bytes, mode)) {
            stop = if (mode == ManagedIndexMode.CONTINUOUS) "SESSION_BATCH_LIMIT"
                else "SESSION_LIMIT"
        }
        return snapshot(code = stop)
    }
}
