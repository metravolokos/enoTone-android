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
class QueueStateFileStore(context: Context) {
    private val directory = context.noBackupFilesDir
    private val queueFile = File(directory, "enotone-queue-v1.json")
    private val positionFile = File(directory, "enotone-position-v1.json")

    fun read(): PersistedQueueState? = try {
        if (!queueFile.isFile || queueFile.length() > 2_000_000L) null
        else {
            val position = runCatching {
                if (!positionFile.isFile || positionFile.length() > 8_192L) null
                else positionFile.readText(Charsets.UTF_8)
            }.getOrNull()
            QueueStateCodec.assemble(queueFile.readText(Charsets.UTF_8), position)
        }
    } catch (_: Exception) {
        null
    }

    fun writeLayout(layout: PersistedQueueLayout) {
        atomicWrite(queueFile, QueueStateCodec.encodeLayout(layout))
    }

    fun writePosition(position: PersistedPositionState) {
        atomicWrite(positionFile, QueueStateCodec.encodePosition(position))
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
