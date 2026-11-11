package app.enotone.playback

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Two separately atomic no-backup checkpoints. The I/O executor serializes
 * layout-before-position writes, so queue revision mismatch catches torn pairs.
 * All methods must be called off the main thread.
 */
class QueueStateFileStore private constructor(private val directory: File) {
    constructor(context: Context) : this(context.noBackupFilesDir) {
        QueuePersistenceDiagnostics.initialize(context)
    }
    /** JVM/Robolectric tests use an isolated directory; no Android storage or network. */
    companion object { fun inDirectory(directory: File) = QueueStateFileStore(directory) }
    private val queueFile = File(directory, "enotone-queue-v1.json")
    private val positionFile = File(directory, "enotone-position-v1.json")

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
                QueueStateCodec.inspect(queueFile.readText(Charsets.UTF_8), position)
            }
        } catch (_: Exception) {
            QueueReadResult(null, QueueRestoreCode.LAYOUT_INVALID)
        }
        QueuePersistenceDiagnostics.read(result.code)
        return result
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
            if (!temporary.renameTo(target)) throw IOException("Queue atomic rename failed")
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun clear() {
        queueFile.delete()
        positionFile.delete()
        File(directory, queueFile.name + ".tmp").delete()
        File(directory, positionFile.name + ".tmp").delete()
    }
}
