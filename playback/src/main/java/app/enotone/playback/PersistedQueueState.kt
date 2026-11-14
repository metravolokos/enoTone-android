package app.enotone.playback

import org.json.JSONArray
import org.json.JSONObject

/** Fixed, anonymous recovery reason; no filenames, hashes or credentials. */
enum class QueueRestoreCode {
    NO_LAYOUT_FILE, LAYOUT_INVALID, POSITION_INVALID, REVISION_MISMATCH,
    SHUFFLE_MISMATCH, FOREIGN_VERSION, SOURCE_MISMATCH, NO_TRACKS_IN_ROOM,
    NO_CREDENTIALS, SERVICE_HAS_QUEUE, RESTORED,
}
data class QueueReadResult(val state: PersistedQueueState?, val code: QueueRestoreCode)


/** Immutable queue layout; written only when the order or shuffle mode changes. */
data class PersistedQueueLayout(
    val trackIds: List<String>,
    val shuffledIndices: IntArray?,
    val shuffleEnabled: Boolean,
    val sourceKey: String,
    val queueRevision: String,
    val savedAtEpochMs: Long,
    val version: Int = QueueStateCodec.VERSION,
)

/** Small checkpoint updated every 12 seconds and on track/repeat/pause changes. */
data class PersistedPositionState(
    val selectedTrackId: String,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val savedAtEpochMs: Long,
    val queueRevision: String,
    val version: Int = QueueStateCodec.VERSION,
    /** Last known duration of the selected track; 0 = unknown. Lets the paused,
     *  not-yet-prepared restored player show a correct progress bar without GET. */
    val durationMs: Long = 0L,
)

/** Restored in-memory state; only hashes and numeric playback state cross the disk boundary. */
data class PersistedQueueState(
    val trackIds: List<String>,
    val selectedTrackId: String,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val shuffledIndices: IntArray?,
    val sourceKey: String,
    val savedAtEpochMs: Long,
    val queueRevision: String,
    val version: Int = QueueStateCodec.VERSION,
    val durationMs: Long = 0L,
) {
    fun layout() = PersistedQueueLayout(
        trackIds, shuffledIndices, shuffleEnabled, sourceKey, queueRevision, savedAtEpochMs, version,
    )
    fun position() = PersistedPositionState(
        selectedTrackId, positionMs, repeatMode, shuffleEnabled,
        savedAtEpochMs, queueRevision, version, durationMs,
    )
}

/** Allowlisted JSON trees via org.json (part of Android; JVM tests use org.json:json), no reflection. */
object QueueStateCodec {
    const val VERSION = 1
    private val hash = Regex("[a-f0-9]{64}")
    private val revision = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val layoutFields = setOf(
        "version", "trackIds", "shuffleEnabled", "sourceKey", "queueRevision", "savedAtEpochMs",
    )
    private val positionFields = setOf(
        "version", "selectedTrackId", "positionMs", "repeatMode",
        "shuffleEnabled", "savedAtEpochMs", "queueRevision",
    )

    fun isValidLayout(layout: PersistedQueueLayout): Boolean {
        val ids = layout.trackIds
        if (layout.version != VERSION || ids.isEmpty() || ids.size > PlaybackQueue.MAX_TRACKS ||
            ids.any { !hash.matches(it) } || ids.toSet().size != ids.size ||
            !QueueEditSource.isValid(layout.sourceKey) || !revision.matches(layout.queueRevision) ||
            layout.savedAtEpochMs < 0L) return false
        val indices = layout.shuffledIndices
        return if (layout.shuffleEnabled) {
            indices != null && indices.size == ids.size &&
                indices.all { it in ids.indices } && indices.toSet().size == ids.size
        } else indices == null
    }

    fun isValidPosition(position: PersistedPositionState): Boolean =
        position.version == VERSION && hash.matches(position.selectedTrackId) &&
            position.positionMs >= 0L && position.repeatMode in 0..2 &&
            position.durationMs >= 0L &&
            position.savedAtEpochMs >= 0L && revision.matches(position.queueRevision)

    fun isValid(state: PersistedQueueState): Boolean =
        isValidLayout(state.layout()) && isValidPosition(state.position()) &&
            state.selectedTrackId in state.trackIds

    fun encodeLayout(layout: PersistedQueueLayout): String {
        require(isValidLayout(layout))
        // org.json входит в Android: без отдельной зависимости (ревью «что удалить» 2026-11-13).
        val json = JSONObject()
        json.put("version", layout.version)
        json.put("trackIds", JSONArray().apply { layout.trackIds.forEach { put(it) } })
        json.put("shuffleEnabled", layout.shuffleEnabled)
        if (layout.shuffleEnabled) {
            json.put("shuffledIndices", JSONArray().apply {
                layout.shuffledIndices!!.forEach { put(it) }
            })
        }
        json.put("sourceKey", layout.sourceKey)
        json.put("queueRevision", layout.queueRevision)
        json.put("savedAtEpochMs", layout.savedAtEpochMs)
        return json.toString()
    }

    fun decodeLayout(raw: String): PersistedQueueLayout? = try {
        if (raw.isBlank() || raw.length > 2_000_000) null else {
            val json = JSONObject(raw)
            val enabled = json.getBoolean("shuffleEnabled")
            if (json.keySet() != layoutFields +
                    if (enabled) setOf("shuffledIndices") else emptySet()) null
            else PersistedQueueLayout(
                trackIds = json.getJSONArray("trackIds").let { a -> List(a.length()) { a.getString(it) } },
                shuffledIndices = if (enabled) json.getJSONArray("shuffledIndices")
                    .let { a -> IntArray(a.length()) { a.getInt(it) } } else null,
                shuffleEnabled = enabled,
                sourceKey = json.getString("sourceKey"),
                queueRevision = json.getString("queueRevision"),
                savedAtEpochMs = json.getLong("savedAtEpochMs"),
                version = json.getInt("version"),
            ).takeIf(::isValidLayout)
        }
    } catch (_: Exception) {
        // JSONException на Android — проверяемое исключение, а не RuntimeException.
        null
    }

    fun encodePosition(position: PersistedPositionState): String {
        require(isValidPosition(position))
        val json = JSONObject()
        json.put("version", position.version)
        json.put("selectedTrackId", position.selectedTrackId)
        json.put("positionMs", position.positionMs)
        json.put("repeatMode", position.repeatMode)
        json.put("shuffleEnabled", position.shuffleEnabled)
        json.put("savedAtEpochMs", position.savedAtEpochMs)
        json.put("queueRevision", position.queueRevision)
        if (position.durationMs > 0L) json.put("durationMs", position.durationMs)
        return json.toString()
    }

    fun decodePosition(raw: String): PersistedPositionState? = try {
        if (raw.isBlank() || raw.length > 8_192) null else {
            val json = JSONObject(raw)
            // durationMs is optional: checkpoints written before it existed stay readable.
            val keys = json.keySet()
            if (keys != positionFields && keys != positionFields + "durationMs") null
            else PersistedPositionState(
                selectedTrackId = json.getString("selectedTrackId"),
                positionMs = json.getLong("positionMs"),
                repeatMode = json.getInt("repeatMode"),
                shuffleEnabled = json.getBoolean("shuffleEnabled"),
                savedAtEpochMs = json.getLong("savedAtEpochMs"),
                queueRevision = json.getString("queueRevision"),
                version = json.getInt("version"),
                durationMs = if (json.has("durationMs")) json.getLong("durationMs") else 0L,
            ).takeIf(::isValidPosition)
        }
    } catch (_: Exception) {
        null
    }

    private fun JSONObject.keySet(): Set<String> = keys().asSequence().toSet()

    private fun versionOf(raw: String): Int? = try {
        JSONObject(raw).let { if (it.has("version")) it.getInt("version") else null }
    } catch (_: Exception) {
        null
    }

    private fun foreignVersion(raw: String): Boolean = try {
        JSONObject(raw).let { !it.has("version") || it.getInt("version") != VERSION }
    } catch (_: Exception) {
        false
    }

    fun inspect(layoutRaw: String?, positionRaw: String?): QueueReadResult {
        if (layoutRaw == null) return QueueReadResult(null, QueueRestoreCode.NO_LAYOUT_FILE)
        if (foreignVersion(layoutRaw)) return QueueReadResult(null, QueueRestoreCode.FOREIGN_VERSION)
        val layout = decodeLayout(layoutRaw)
            ?: return QueueReadResult(null, QueueRestoreCode.LAYOUT_INVALID)
        if (positionRaw != null && foreignVersion(positionRaw)) {
            return QueueReadResult(null, QueueRestoreCode.FOREIGN_VERSION)
        }
        val position = positionRaw?.let(::decodePosition)
        if (position != null && position.queueRevision != layout.queueRevision) {
            return QueueReadResult(null, QueueRestoreCode.REVISION_MISMATCH)
        }
        if (position != null && position.shuffleEnabled != layout.shuffleEnabled) {
            return QueueReadResult(null, QueueRestoreCode.SHUFFLE_MISMATCH)
        }
        val reconstructed = assemble(layoutRaw, positionRaw)
        return QueueReadResult(reconstructed,
            if (positionRaw != null && position == null) QueueRestoreCode.POSITION_INVALID
            else QueueRestoreCode.RESTORED)
    }

    private fun hasForeignPositionVersion(raw: String): Boolean =
        versionOf(raw).let { it != null && it != VERSION }

    /**
     * A missing/damaged position is recoverable from the first track at 0.
     * A valid position from a different revision is NOT: it proves torn writes.
     */
    fun assemble(layoutRaw: String, positionRaw: String?): PersistedQueueState? {
        val layout = decodeLayout(layoutRaw) ?: return null
        // A syntactically valid newer position format must not be mistaken for
        // corruption: unknown versions remain unsupported (no state).
        if (positionRaw != null && hasForeignPositionVersion(positionRaw)) return null
        val position = positionRaw?.let(::decodePosition)
        if (position != null &&
            (position.queueRevision != layout.queueRevision ||
                position.shuffleEnabled != layout.shuffleEnabled)) return null
        val safePosition = position?.takeIf { it.selectedTrackId in layout.trackIds }
            ?: PersistedPositionState(
                layout.trackIds.first(), 0L, 0, layout.shuffleEnabled,
                layout.savedAtEpochMs, layout.queueRevision,
            )
        return PersistedQueueState(
            trackIds = layout.trackIds,
            selectedTrackId = safePosition.selectedTrackId,
            positionMs = safePosition.positionMs,
            repeatMode = safePosition.repeatMode,
            shuffleEnabled = layout.shuffleEnabled,
            shuffledIndices = layout.shuffledIndices,
            sourceKey = layout.sourceKey,
            savedAtEpochMs = safePosition.savedAtEpochMs,
            queueRevision = layout.queueRevision,
            durationMs = safePosition.durationMs,
        )
    }
}

/** All indices are rebased to the filtered original (not shuffle) order. */
object QueueStateRestorer {
    fun filter(state: PersistedQueueState, availableIds: Set<String>): PersistedQueueState? {
        if (!QueueStateCodec.isValid(state)) return null
        val survivors = state.trackIds.filter { it in availableIds }
        if (survivors.isEmpty()) return null
        val oldIndex = state.trackIds.withIndex().associate { it.value to it.index }
        val newIndex = survivors.withIndex().associate { oldIndex.getValue(it.value) to it.index }
        val selectedExists = state.selectedTrackId in survivors
        val selected = if (selectedExists) state.selectedTrackId else {
            val from = oldIndex.getValue(state.selectedTrackId)
            state.trackIds.drop(from + 1).firstOrNull { it in availableIds }
                ?: survivors.first()
        }
        val reordered = if (state.shuffleEnabled) {
            state.shuffledIndices!!.toList().mapNotNull { newIndex[it] }.toIntArray()
        } else null
        return state.copy(
            trackIds = survivors,
            selectedTrackId = selected,
            positionMs = if (selectedExists) state.positionMs else 0L,
            durationMs = if (selectedExists) state.durationMs else 0L,
            shuffledIndices = reordered,
        )
    }
}

/** Injection point for deterministic, Android-free write-rate tests. */
fun interface QueueMonotonicClock { fun nowMs(): Long }

class QueueWriteThrottle(
    private val clock: QueueMonotonicClock,
    private val minIntervalMs: Long = 5_000L,
) {
    private var lastWriteMs: Long? = null

    fun delayMs(urgent: Boolean = false): Long {
        if (urgent) return 0L
        val last = lastWriteMs ?: return 0L
        return (minIntervalMs - (clock.nowMs() - last)).coerceAtLeast(0L)
    }

    fun markWritten() { lastWriteMs = clock.nowMs() }
}
