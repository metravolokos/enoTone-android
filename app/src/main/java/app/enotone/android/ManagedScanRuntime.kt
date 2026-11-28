package app.enotone.android

import android.database.sqlite.SQLiteException
import app.enotone.library.ManagedIndexProgress
import app.enotone.library.TagBatchStageException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Injected by the runner so JVM tests do not share the production singleton. */
internal interface ManagedScanStatusPort {
    val status: StateFlow<ManagedScanStatus>
    fun update(
        state: ManagedScanState,
        progress: ManagedIndexProgress? = null,
        terminating: Boolean = false,
    )
    fun fail(stage: String, cause: Exception)
}

internal enum class ManagedScanState { IDLE, RUNNING, PAUSED, STOPPED, COMPLETE, ERROR }

internal data class ManagedScanStatus(
    val state: ManagedScanState = ManagedScanState.IDLE,
    val progress: ManagedIndexProgress? = null,
    val terminating: Boolean = false,
    // Fixed codes only: never a Throwable message, URL, username or file name.
    val failureStage: String? = null,
    val failureReason: String? = null,
)

/**
 * The password never enters an Intent, PendingIntent, log or notification.
 * Background execution survives Activity.onStop(), not process death.
 * Completed tag checkpoints are independently durable in Room.
 */
internal data class PendingIndexRun(
    val login: StoredWebDavLogin,
)

internal object ManagedScanRuntime : ManagedScanStatusPort {
    override val status = MutableStateFlow(ManagedScanStatus())

    // One-shot RAM handoff, never an Intent extra or persisted secret.
    private var pending: PendingIndexRun? = null

    @Synchronized fun prepare(
        email: String,
        password: String,
        endpoint: SourceEndpoint = SourceEndpoint.MAILRU_ALL_MUSIC,
    ): Boolean {
        require(email.isNotBlank() && password.isNotBlank())
        if (status.value.state == ManagedScanState.RUNNING || status.value.terminating) return false
        pending = PendingIndexRun(StoredWebDavLogin(email, password, endpoint))
        status.value = ManagedScanStatus(ManagedScanState.RUNNING)
        return true
    }

    @Synchronized fun claim(): PendingIndexRun? =
        pending.also { pending = null }

    @Synchronized override fun update(
        state: ManagedScanState,
        progress: ManagedIndexProgress?,
        terminating: Boolean,
    ) {
        // A delayed progress callback must never undo a user's Pause/Stop.
        if (state == ManagedScanState.RUNNING &&
            (status.value.state != ManagedScanState.RUNNING || status.value.terminating)) return
        if (state != ManagedScanState.RUNNING) pending = null
        val last = status.value
        // onDestroy() passes no progress, so the code to preserve must be read from the last
        // snapshot as well; otherwise the teardown would rewrite a budget pause to "PAUSED".
        val terminalCode = terminalCodeFor(state, progress ?: last.progress)
        val finalProgress = progress ?: last.progress?.let {
            if (terminalCode == null) it else it.copy(stopCode = terminalCode)
        }
        status.value = ManagedScanStatus(
            state, finalProgress, terminating,
            failureStage = if (state == last.state) last.failureStage else null,
            failureReason = if (state == last.state) last.failureReason else null,
        )
    }

    /**
     * The stop code a terminal snapshot carries. A budget pause keeps its own code so the
     * first-run resume guard can tell it from the user pressing Pause, which stays sticky.
     */
    private fun terminalCodeFor(
        state: ManagedScanState,
        progress: ManagedIndexProgress?,
    ): String? = when (state) {
        ManagedScanState.PAUSED -> progress?.stopCode
            ?.takeIf { it.startsWith(SESSION_LIMIT_PREFIX) } ?: "PAUSED"
        ManagedScanState.STOPPED -> "STOPPED"
        ManagedScanState.ERROR -> "FAILED_INTERNAL"
        else -> null
    }

    @Synchronized override fun fail(stage: String, cause: Exception) {
        // Classify only exception types, NEVER exception messages/stack traces
        // that might contain private remote paths or authorization details.
        val source = if (cause is TagBatchStageException)
            cause.cause as? Exception ?: cause else cause
        val location = if (cause is TagBatchStageException) cause.phase else stage
        val reason = when (source) {
            is SQLiteException -> "SQLITE"
            is SecurityException -> "SECURITY"
            is java.io.IOException -> "IO"
            is IllegalArgumentException -> "ARGUMENT"
            is IllegalStateException -> "STATE"
            else -> "UNEXPECTED"
        }
        pending = null
        val last = status.value
        val stopped = last.progress?.copy(
            stopCode = "FAILED_INTERNAL",
            lastErrorStage = location,
            lastErrorReason = reason,
            lastErrorHttpStatus = 0,
        )
        status.value = ManagedScanStatus(
            ManagedScanState.ERROR, stopped, terminating = true,
            failureStage = location, failureReason = reason,
        )
    }
}
