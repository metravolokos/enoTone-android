package app.enotone.playback

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/** File operations must be invoked on the service's single I/O executor. */
internal class QueueStateFileStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "enotone-queue-v1.json")
    private val temp = File(context.noBackupFilesDir, "enotone-queue-v1.json.tmp")

    fun read(): PersistedQueueState? = try {
        if (!file.isFile || file.length() > 2_000_000L) null
        else QueueStateCodec.decode(file.readText(Charsets.UTF_8))
    } catch (_: Exception) {
        null
    }

    fun write(state: PersistedQueueState) {
        val bytes = QueueStateCodec.encode(state).toByteArray(Charsets.UTF_8)
        try {
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (!temp.renameTo(file)) {
                // Never replace the last valid snapshot with a partial write.
                throw java.io.IOException("Atomic queue rename failed")
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun clear() {
        file.delete()
        temp.delete()
    }
}
