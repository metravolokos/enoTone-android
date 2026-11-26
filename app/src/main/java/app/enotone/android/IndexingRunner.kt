package app.enotone.android

import app.enotone.database.EnoToneDatabase
import app.enotone.library.ManagedIndexProgress
import app.enotone.library.ManagedTagIndexSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** Data and network are provided through ports; no Android Service or Context. */
internal fun interface IndexingSessionPort {
    suspend fun run(
        request: PendingIndexRun,
        progress: suspend (ManagedIndexProgress) -> Unit,
    ): ManagedIndexProgress
}

internal fun interface IndexingSessionFactory {
    suspend fun open(): IndexingSessionPort
}

/** Database access is delayed until the foreground notification is established. */
internal class RoomIndexingSessionFactory(
    private val database: () -> EnoToneDatabase,
    private val networkAllowed: () -> Boolean = { true },
) : IndexingSessionFactory {
    override suspend fun open(): IndexingSessionPort {
        val db = database()
        return IndexingSessionPort { request, progress ->
            ManagedTagIndexSession(db).run(
                request.login.email,
                request.login.password,
                networkAllowed = networkAllowed,
                onProgress = progress,
            )
        }
    }
}

/**
 * Budget pauses stop at a bounded limit and resume from their checkpoint. The prefix is shared
 * with the first-run resume guard, which must not confuse a budget pause with a user pressing
 * Pause — that one stays sticky.
 */
internal const val SESSION_LIMIT_PREFIX = "SESSION_"

/**
 * Runs one explicit tag-indexing session. A gate prevents overlapping sessions
 * even if two starts race before the Android service stores its Job.
 * Room checkpoints are written by ManagedTagIndexSession before progress.
 */
internal class IndexingRunner(
    private val sessions: IndexingSessionFactory,
    private val signOutPending: () -> Boolean,
    private val statusPort: ManagedScanStatusPort = ManagedScanRuntime,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val gate = Mutex()

    suspend fun run(
        request: PendingIndexRun,
        publishProgress: suspend (ManagedIndexProgress) -> Unit = {},
    ): Boolean {
        if (!gate.tryLock()) return false
        return try {
            when {
                statusPort.status.value.state != ManagedScanState.RUNNING ||
                    statusPort.status.value.terminating -> false
                signOutPending() -> {
                    statusPort.update(ManagedScanState.PAUSED)
                    false
                }
                else -> execute(request, publishProgress)
            }
        } finally {
            gate.unlock()
        }
    }

    /** All unexpected exceptions must be classified without leaking private paths. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun execute(
        request: PendingIndexRun,
        publishProgress: suspend (ManagedIndexProgress) -> Unit,
    ): Boolean {
        var stage = "DATABASE_OPEN"
        return try {
            withContext(dispatcher) {
                val session = sessions.open()
                stage = "INDEX_BATCH"
                val final = session.run(request) { progress ->
                    if (statusPort.status.value.state == ManagedScanState.RUNNING &&
                        !statusPort.status.value.terminating) {
                        statusPort.update(ManagedScanState.RUNNING, progress)
                        publishProgress(progress)
                    }
                }
                if (statusPort.status.value.state == ManagedScanState.RUNNING) {
                    // COMPLETE is an allow-list, not a default: a stop code this mapping does
                    // not know must never announce a finished library. Session limits are a
                    // bounded stop that resumes from the checkpoint, so they are a pause.
                    val state = when (final.stopCode) {
                        in COMPLETION_CODES -> ManagedScanState.COMPLETE
                        in TERMINAL_ERROR_CODES -> ManagedScanState.ERROR
                        else -> ManagedScanState.PAUSED
                    }
                    statusPort.update(state, final, terminating = true)
                }
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (statusPort.status.value.state == ManagedScanState.RUNNING) {
                statusPort.fail(stage, failure)
            }
            false
        }
    }

    fun pause(jobActive: Boolean) =
        statusPort.update(ManagedScanState.PAUSED, terminating = jobActive)

    fun stop(jobActive: Boolean) =
        statusPort.update(ManagedScanState.STOPPED, terminating = jobActive)

    fun onTimeout(jobActive: Boolean) = pause(jobActive)

    fun onDestroy() {
        val last = statusPort.status.value
        if (last.state == ManagedScanState.RUNNING) {
            statusPort.update(ManagedScanState.PAUSED)
        } else if (last.terminating) {
            // A fresh start is safe only after the old service has stopped.
            statusPort.update(last.state, terminating = false)
        }
    }

    private companion object {
        /** The run cannot continue without a user decision; retrying blindly would spin. */
        val TERMINAL_ERROR_CODES = setOf("AUTH_REJECTED", "RETRY_BACKOFF", "NO_PROGRESS")

        /** Only these mean the library has nothing left to read. */
        val COMPLETION_CODES = setOf("NO_ELIGIBLE_FILES", "COMPLETED")
    }
}
