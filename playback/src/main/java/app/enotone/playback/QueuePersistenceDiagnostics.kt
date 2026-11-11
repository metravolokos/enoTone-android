package app.enotone.playback

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Counters and fixed codes only; survives process death without storing private identifiers. */
data class QueuePersistenceSnapshot(
    val result: QueueRestoreCode = QueueRestoreCode.NO_LAYOUT_FILE,
    val readResult: QueueRestoreCode = QueueRestoreCode.NO_LAYOUT_FILE,
    val layoutWrites: Long = 0,
    val positionWrites: Long = 0,
    val writeErrors: Long = 0,
    val lastExceptionClass: String = "NONE",
)

object QueuePersistenceDiagnostics {
    private val mutable = MutableStateFlow(QueuePersistenceSnapshot())
    val state: StateFlow<QueuePersistenceSnapshot> = mutable
    private var prefs: SharedPreferences? = null

    @Synchronized fun initialize(context: Context) {
        if (prefs != null) return
        val saved = context.applicationContext.getSharedPreferences(
            "enotone_queue_diagnostics_v1", Context.MODE_PRIVATE)
        prefs = saved
        val result = runCatching {
            QueueRestoreCode.valueOf(saved.getString("result", "NO_LAYOUT_FILE")!!)
        }.getOrDefault(QueueRestoreCode.NO_LAYOUT_FILE)
        val read = runCatching {
            QueueRestoreCode.valueOf(saved.getString("read", "NO_LAYOUT_FILE")!!)
        }.getOrDefault(QueueRestoreCode.NO_LAYOUT_FILE)
        mutable.value = QueuePersistenceSnapshot(result, read,
            saved.getLong("layoutWrites", 0), saved.getLong("positionWrites", 0),
            saved.getLong("writeErrors", 0), saved.getString("errorClass", "NONE") ?: "NONE")
    }

    @Synchronized private fun publish(value: QueuePersistenceSnapshot) {
        mutable.value = value
        prefs?.edit()?.putString("result", value.result.name)
            ?.putString("read", value.readResult.name)
            ?.putLong("layoutWrites", value.layoutWrites)
            ?.putLong("positionWrites", value.positionWrites)
            ?.putLong("writeErrors", value.writeErrors)
            ?.putString("errorClass", value.lastExceptionClass)
            ?.apply()
    }

    @Synchronized fun result(code: QueueRestoreCode) =
        publish(mutable.value.copy(result = code))
    @Synchronized fun read(code: QueueRestoreCode) =
        publish(mutable.value.copy(readResult = code, result = code))
    @Synchronized fun layoutWritten() =
        publish(mutable.value.copy(layoutWrites = mutable.value.layoutWrites + 1))
    @Synchronized fun positionWritten() =
        publish(mutable.value.copy(positionWrites = mutable.value.positionWrites + 1))
    @Synchronized fun writeFailed(error: Exception) =
        publish(mutable.value.copy(writeErrors = mutable.value.writeErrors + 1,
            lastExceptionClass = error.javaClass.simpleName.ifEmpty { "Exception" }))
}
