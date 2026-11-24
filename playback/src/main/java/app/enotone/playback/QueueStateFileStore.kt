package app.enotone.playback

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Two separately atomic no-backup checkpoints. The I/O executor serializes
 * layout-before-position writes; a torn pair (process death in between) falls back
 * to the previous layout kept on disk, whose revision still matches the old position.
 * All methods must be called off the main thread.
 */
class QueueStateFileStore private constructor(
    private val directory: File,
    // Deterministic JVM fault injection; production uses the no-op default.
    private val beforePositionWrite: () -> Unit = {},
) {
    constructor(context: Context) : this(context.noBackupFilesDir) {
        QueuePersistenceDiagnostics.initialize(context)
    }
    /** JVM/Robolectric tests use an isolated directory; no Android storage or network. */
    companion object {
        private const val MAX_LAYOUT_BYTES = 2_000_000L

        /** A writer that is still alive can recreate the file; a few retries settle it. */
        private const val CLEAR_ATTEMPTS = 5
        private const val CLEAR_RETRY_MS = 20L

        fun inDirectory(directory: File, beforePositionWrite: () -> Unit = {}) =
            QueueStateFileStore(directory, beforePositionWrite)
    }
    private val queueFile = File(directory, "enotone-queue-v1.json")
    private val positionFile = File(directory, "enotone-position-v1.json")

    // Предыдущая структура очереди: если процесс погиб между заменой структуры и позиции,
    // старая позиция по-прежнему совпадает с ней по ревизии (внешний аудит 2026-11-13, п. 7).
    private val previousQueueFile = File(directory, "enotone-queue-v1.prev.json")

    fun read(): PersistedQueueState? = readWithStatus().state

    fun readWithStatus(): QueueReadResult {
        val result = try {
            if (!queueFile.isFile) QueueReadResult(null, QueueRestoreCode.NO_LAYOUT_FILE)
            else if (queueFile.length() > 2_000_000L) {
                QueueReadResult(null, QueueRestoreCode.LAYOUT_INVALID)
            } else {
                val position = runCatching {
                    if (!positionFile.isFile || positionFile.length() > 8_192L) null
                    else positionFile.readText(Charsets.UTF_8)
                }.getOrNull()
                val current = QueueStateCodec.inspect(queueFile.readText(Charsets.UTF_8), position)
                if (current.code == QueueRestoreCode.REVISION_MISMATCH) previousPair(position) ?: current
                else current
            }
        } catch (_: Exception) {
            QueueReadResult(null, QueueRestoreCode.LAYOUT_INVALID)
        }
        QueuePersistenceDiagnostics.read(result.code)
        return result
    }

    /** Оборванная пара: позиция старой ревизии + новая структура → восстанавливаем старую пару целиком. */
    private fun previousPair(position: String?): QueueReadResult? {
        if (position == null || !previousQueueFile.isFile || previousQueueFile.length() > MAX_LAYOUT_BYTES) return null
        val previous = QueueStateCodec.inspect(previousQueueFile.readText(Charsets.UTF_8), position)
        return previous.takeIf { it.code == QueueRestoreCode.RESTORED }
    }

    fun writeLayout(layout: PersistedQueueLayout) {
        try {
            atomicWrite(queueFile, QueueStateCodec.encodeLayout(layout))
            QueuePersistenceDiagnostics.layoutWritten()
        } catch (error: Exception) {
            QueuePersistenceDiagnostics.writeFailed(error)
            throw error
        }
    }

    fun writePosition(position: PersistedPositionState) {
        try {
            beforePositionWrite()
            atomicWrite(positionFile, QueueStateCodec.encodePosition(position))
            QueuePersistenceDiagnostics.positionWritten()
        } catch (error: Exception) {
            QueuePersistenceDiagnostics.writeFailed(error)
            throw error
        }
    }

    /**
     * Complete a two-file checkpoint on one IO executor. Keep the old layout
     * until the new position is committed, and roll back when position write
     * fails. A bad partial state must never become the current revision.
     */
    fun writeLayoutAndPosition(
        layout: PersistedQueueLayout,
        position: PersistedPositionState,
    ) {
        require(layout.queueRevision == position.queueRevision &&
            layout.shuffleEnabled == position.shuffleEnabled)
        val oldLayout = if (queueFile.isFile) queueFile.readText(Charsets.UTF_8) else null
        val oldPosition = if (positionFile.isFile) positionFile.readText(Charsets.UTF_8) else null
        if (oldLayout != null) atomicWrite(previousQueueFile, oldLayout) else previousQueueFile.delete()
        writeLayout(layout)
        try {
            writePosition(position)
        } catch (error: Exception) {
            runCatching {
                if (oldLayout != null) atomicWrite(queueFile, oldLayout) else queueFile.delete()
                if (oldPosition != null) atomicWrite(positionFile, oldPosition)
                else positionFile.delete()
            }.onFailure { problem ->
                if (problem is Exception) QueuePersistenceDiagnostics.writeFailed(problem)
            }
            throw error
        }
    }

    private fun atomicWrite(target: File, contents: String) {
        val temporary = File(directory, target.name + ".tmp")
        try {
            FileOutputStream(temporary).use { out ->
                out.write(contents.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            // File.renameTo refuses to overwrite an existing target on Windows, so the
            // second checkpoint always failed there. See AtomicFileReplace.
            if (!AtomicFileReplace.replace(temporary, target)) {
                throw IOException("Queue atomic rename failed")
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun clear() {
        // Sign-out must not report success while a stale checkpoint survives: it would be
        // restored on the next launch, after the Keystore is already gone. Absent files are
        // success; only a file that still exists after deletion is a failure. Checking
        // delete()'s own return value would break idempotency, because a second sign-out
        // finds the files already missing.
        val targets = listOf(
            queueFile,
            positionFile,
            previousQueueFile,
            File(directory, previousQueueFile.name + ".tmp"),
            File(directory, queueFile.name + ".tmp"),
            File(directory, positionFile.name + ".tmp"),
        )
        // A live service may rewrite a checkpoint right after deletion: this store has no
        // way to stop the writer. Retry a few times instead of failing on that race, and
        // fail only when the file is still there after the last attempt.
        repeat(CLEAR_ATTEMPTS) { attempt ->
            val stale = targets.firstOrNull { it.exists() && !it.delete() }
            if (stale == null && targets.none { it.exists() }) return
            if (attempt == CLEAR_ATTEMPTS - 1 && targets.any { it.exists() }) {
                throw IOException("Cannot remove queue checkpoint")
            }
            Thread.sleep(CLEAR_RETRY_MS)
        }
    }
}
