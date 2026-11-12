package app.enotone.playback

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

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
) {
    fun layout() = PersistedQueueLayout(
        trackIds, shuffledIndices, shuffleEnabled, sourceKey, queueRevision, savedAtEpochMs, version,
    )
    fun position() = PersistedPositionState(
        selectedTrackId, positionMs, repeatMode, shuffleEnabled,
        savedAtEpochMs, queueRevision, version,
    )
}

/** Allowlisted JSON trees, with no reflection and no Android runtime dependency. */
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
            !hash.matches(layout.sourceKey) || !revision.matches(layout.queueRevision) ||
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
            position.savedAtEpochMs >= 0L && revision.matches(position.queueRevision)

    fun isValid(state: PersistedQueueState): Boolean =
        isValidLayout(state.layout()) && isValidPosition(state.position()) &&
            state.selectedTrackId in state.trackIds

    fun encodeLayout(layout: PersistedQueueLayout): String {
        require(isValidLayout(layout))
        val json = JsonObject()
        json.addProperty("version", layout.version)
        json.add("trackIds", JsonArray().apply { layout.trackIds.forEach(::add) })
        json.addProperty("shuffleEnabled", layout.shuffleEnabled)
        if (layout.shuffleEnabled) {
            json.add("shuffledIndices", JsonArray().apply {
                layout.shuffledIndices!!.forEach(::add)
            })
        }
        json.addProperty("sourceKey", layout.sourceKey)
        json.addProperty("queueRevision", layout.queueRevision)
        json.addProperty("savedAtEpochMs", layout.savedAtEpochMs)
        return json.toString()
    }

    fun decodeLayout(raw: String): PersistedQueueLayout? = try {
        if (raw.isBlank() || raw.length > 2_000_000) null else {
            val json = JsonParser.parseString(raw).asJsonObject
            val enabled = json.get("shuffleEnabled").asBoolean
            if (json.keySet() != layoutFields +
                    if (enabled) setOf("shuffledIndices") else emptySet()) null
            else PersistedQueueLayout(
                trackIds = json.getAsJsonArray("trackIds").map { it.asString },
                shuffledIndices = if (enabled) json.getAsJsonArray("shuffledIndices")
                    .map { it.asInt }.toIntArray() else null,
                shuffleEnabled = enabled,
                sourceKey = json.get("sourceKey").asString,
                queueRevision = json.get("queueRevision").asString,
                savedAtEpochMs = json.get("savedAtEpochMs").asLong,
                version = json.get("version").asInt,
            ).takeIf(::isValidLayout)
        }
    } catch (_: RuntimeException) {
        null
    }

    fun encodePosition(position: PersistedPositionState): String {
        require(isValidPosition(position))
        val json = JsonObject()
        json.addProperty("version", position.version)
        json.addProperty("selectedTrackId", position.selectedTrackId)
        json.addProperty("positionMs", position.positionMs)
        json.addProperty("repeatMode", position.repeatMode)
        json.addProperty("shuffleEnabled", position.shuffleEnabled)
        json.addProperty("savedAtEpochMs", position.savedAtEpochMs)
        json.addProperty("queueRevision", position.queueRevision)
        return json.toString()
    }

    fun decodePosition(raw: String): PersistedPositionState? = try {
        if (raw.isBlank() || raw.length > 8_192) null else {
            val json = JsonParser.parseString(raw).asJsonObject
            if (json.keySet() != positionFields) null else PersistedPositionState(
                selectedTrackId = json.get("selectedTrackId").asString,
                positionMs = json.get("positionMs").asLong,
                repeatMode = json.get("repeatMode").asInt,
                shuffleEnabled = json.get("shuffleEnabled").asBoolean,
                savedAtEpochMs = json.get("savedAtEpochMs").asLong,
                queueRevision = json.get("queueRevision").asString,
                version = json.get("version").asInt,
            ).takeIf(::isValidPosition)
        }
    } catch (_: RuntimeException) {
        null
    }

    private fun foreignVersion(raw: String): Boolean = try {
        JsonParser.parseString(raw).asJsonObject.get("version")?.asInt != VERSION
    } catch (_: RuntimeException) {
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

    private fun hasForeignPositionVersion(raw: String): Boolean = try {
        val version = JsonParser.parseString(raw).asJsonObject.get("version")
        version != null && version.asInt != VERSION
    } catch (_: RuntimeException) {
        false
    }

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
