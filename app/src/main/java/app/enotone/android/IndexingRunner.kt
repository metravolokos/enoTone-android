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
                    val error = final.stopCode in setOf(
                        "AUTH_REJECTED", "RETRY_BACKOFF", "NO_PROGRESS",
                    )
                    statusPort.update(
                        if (error) ManagedScanState.ERROR else ManagedScanState.COMPLETE,
                        final,
                        terminating = true,
                    )
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
}
