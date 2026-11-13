package app.enotone.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Fakes can substitute album groups and storage without Android or HTTP. */
internal interface ArtworkPreparationPort<Group> {
    suspend fun groups(login: StoredWebDavLogin): List<Group>
    suspend fun isIndexed(login: StoredWebDavLogin, group: Group): Boolean
    suspend fun prepare(login: StoredWebDavLogin, group: Group)
    fun tagsRunning(): Boolean
    suspend fun publish(completed: Int, total: Int)
}

/**
 * The same permanent album checkpoints (including NO_ART) remain owned by
 * ArtworkPreviewRepository, not by this runner. Tag scans have network priority.
 */
internal class ArtworkPreparationRunner<Group>(
    private val port: ArtworkPreparationPort<Group>,
    private val signOutPending: () -> Boolean,
    private val statusPort: ArtworkScanStatusPort = ArtworkScanRuntime,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowNanos: () -> Long = System::nanoTime,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val gate = Mutex()

    suspend fun run(login: StoredWebDavLogin): Boolean {
        if (!gate.tryLock()) return false
        return try {
            when {
                statusPort.status.value.state != ArtworkScanState.RUNNING ||
                    statusPort.status.value.terminating -> false
                signOutPending() -> {
                    statusPort.update(ArtworkScanState.PAUSED, 0, 0)
                    false
                }
                else -> execute(login)
            }
        } finally {
            gate.unlock()
        }
    }

    private suspend fun execute(login: StoredWebDavLogin): Boolean {
        var completed = 0
        var total = 0
        return try {
            withContext(dispatcher) {
                val groups = port.groups(login)
                total = groups.size
                val startedAt = nowNanos()
                for (group in groups) {
                    coroutineContext.ensureActive()
                    if (nowNanos() - startedAt >= MAX_SESSION_NANOS) break
                    if (prepareOne(login, group)) completed++
                    statusPort.update(ArtworkScanState.RUNNING, completed, total)
                    if (completed % NOTIFICATION_STEP == 0 || completed == total) {
                        port.publish(completed, total)
                    }
                }
                if (statusPort.status.value.state == ArtworkScanState.RUNNING) {
                    statusPort.update(
                        if (completed == total) ArtworkScanState.COMPLETE
                        else ArtworkScanState.PAUSED,
                        completed, total, terminating = true,
                    )
                }
            }
            true
        } catch (cancelled: CancellationException) {
            // Pause/Stop already published the terminal state before cancellation.
            throw cancelled
        } catch (_: Exception) {
            if (statusPort.status.value.state == ArtworkScanState.RUNNING) {
                statusPort.update(ArtworkScanState.ERROR, completed, total, terminating = true)
            }
            false
        }
    }

    private suspend fun prepareOne(login: StoredWebDavLogin, group: Group): Boolean {
        // Tag scans retain network priority even when artwork was queued first.
        while (port.tagsRunning()) {
            sleep(WAIT_FOR_TAGS_MS)
            coroutineContext.ensureActive()
        }
        if (!port.isIndexed(login, group)) {
            port.prepare(login, group)
            sleep(BETWEEN_ALBUM_MS)
        }
        return port.isIndexed(login, group)
    }
    fun pause(jobActive: Boolean) = finish(ArtworkScanState.PAUSED, jobActive)
    fun stop(jobActive: Boolean) = finish(ArtworkScanState.STOPPED, jobActive)
    fun onTimeout(jobActive: Boolean) = pause(jobActive)

    private fun finish(state: ArtworkScanState, jobActive: Boolean) {
        val last = statusPort.status.value
        statusPort.update(state, last.completed, last.total, terminating = jobActive)
    }

    fun onDestroy() {
        val last = statusPort.status.value
        if (last.state == ArtworkScanState.RUNNING) {
            statusPort.update(ArtworkScanState.PAUSED, last.completed, last.total)
        } else if (last.terminating) {
            statusPort.update(last.state, last.completed, last.total, terminating = false)
        }
    }

    companion object {
        const val MAX_SESSION_NANOS = 4L * 60 * 60 * 1_000_000_000
        const val WAIT_FOR_TAGS_MS = 1200L
        const val BETWEEN_ALBUM_MS = 200L
        const val NOTIFICATION_STEP = 5
    }
}
