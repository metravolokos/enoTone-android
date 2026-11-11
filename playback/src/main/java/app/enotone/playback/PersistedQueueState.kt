package app.enotone.playback

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Only SHA-256 Room IDs and non-secret playback state may cross the disk boundary. */
data class PersistedQueueState(
    val trackIds: List<String>,
    val selectedTrackId: String,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val shuffledIndices: IntArray?,
    val sourceKey: String,
    val savedAtEpochMs: Long,
    val version: Int = QueueStateCodec.VERSION,
)

/** Pure JVM codec: a strict allowlist prevents accidental persistence of metadata. */
object QueueStateCodec {
    const val VERSION = 1
    private val hash = Regex("[a-f0-9]{64}")
    private val commonFields = setOf(
        "version", "trackIds", "selectedTrackId", "positionMs", "repeatMode",
        "shuffleEnabled", "sourceKey", "savedAtEpochMs",
    )

    fun encode(state: PersistedQueueState): String {
        require(isValid(state)) { "Invalid queue snapshot" }
        val json = JsonObject()
        json.addProperty("version", state.version)
        json.add("trackIds", JsonArray().apply { state.trackIds.forEach(::add) })
        json.addProperty("selectedTrackId", state.selectedTrackId)
        json.addProperty("positionMs", state.positionMs)
        json.addProperty("repeatMode", state.repeatMode)
        json.addProperty("shuffleEnabled", state.shuffleEnabled)
        if (state.shuffleEnabled) {
            json.add("shuffledIndices", JsonArray().apply {
                state.shuffledIndices!!.forEach(::add)
            })
        }
        json.addProperty("sourceKey", state.sourceKey)
        json.addProperty("savedAtEpochMs", state.savedAtEpochMs)
        return json.toString()
    }

    /** Missing, damaged or unknown-version snapshots never propagate an exception. */
    fun decode(contents: String): PersistedQueueState? = try {
        if (contents.isBlank() || contents.length > 2_000_000) null else {
            val json = JsonParser.parseString(contents).asJsonObject
            val version = json.get("version").asInt
            val shuffle = json.get("shuffleEnabled").asBoolean
            val fields = commonFields + if (shuffle) setOf("shuffledIndices") else emptySet()
            if (version != VERSION || json.keySet() != fields) null else {
                val ids = json.getAsJsonArray("trackIds").map { it.asString }
                val indices = if (shuffle) json.getAsJsonArray("shuffledIndices")
                    .map { it.asInt }.toIntArray() else null
                val state = PersistedQueueState(
                    trackIds = ids,
                    selectedTrackId = json.get("selectedTrackId").asString,
                    positionMs = json.get("positionMs").asLong,
                    repeatMode = json.get("repeatMode").asInt,
                    shuffleEnabled = shuffle,
                    shuffledIndices = indices,
                    sourceKey = json.get("sourceKey").asString,
                    savedAtEpochMs = json.get("savedAtEpochMs").asLong,
                    version = version,
                )
                state.takeIf(::isValid)
            }
        }
    } catch (_: RuntimeException) {
        null
    }

    fun isValid(state: PersistedQueueState): Boolean {
        val ids = state.trackIds
        if (state.version != VERSION || ids.isEmpty() || ids.size > PlaybackQueue.MAX_TRACKS ||
            ids.any { !hash.matches(it) } || ids.toSet().size != ids.size ||
            state.selectedTrackId !in ids || !hash.matches(state.sourceKey) ||
            state.positionMs < 0L || state.savedAtEpochMs < 0L ||
            state.repeatMode !in 0..2) return false
        val order = state.shuffledIndices
        return if (state.shuffleEnabled) {
            order != null && order.size == ids.size &&
                order.all { it in ids.indices } && order.toSet().size == ids.size
        } else order == null
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
            state.shuffledIndices!!.mapNotNull { newIndex[it] }.toIntArray()
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
fun interface QueueMonotonicClock {
    fun nowMs(): Long
}

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

    fun markWritten() {
        lastWriteMs = clock.nowMs()
    }
}
