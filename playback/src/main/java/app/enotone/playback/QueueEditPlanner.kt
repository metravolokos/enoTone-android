package app.enotone.playback

/** Physical Media3 order and (when enabled) the exact, stable playback permutation. */
data class QueueLayout(
    val trackIds: List<String>,
    val shuffledIndices: IntArray?,
    val currentTrackId: String?,
) {
    fun playbackIds(): List<String> =
        shuffledIndices?.map { trackIds[it] } ?: trackIds

    val shuffleEnabled: Boolean get() = shuffledIndices != null
}

sealed interface QueueEdit {
    val ids: List<String>

    data class PlayNext(override val ids: List<String>) : QueueEdit
    data class Append(override val ids: List<String>) : QueueEdit
}

enum class QueueEditStatus {
    APPLIED, ALREADY_PLAYING, QUEUE_FULL, EMPTY_SELECTION, UNAVAILABLE,
}

/** Number of selected non-current tracks actually relocated or added. */
data class QueueEditResult(val status: QueueEditStatus, val count: Int = 0)

data class QueueEditPlan(
    val layout: QueueLayout,
    val movedIds: List<String>,
    val result: QueueEditResult,
)

/**
 * Pure planner. A moved track occurs only once. Shuffle changes the traversal,
 * never randomly inserts new items among already scheduled songs.
 */
object QueueEditPlanner {
    fun plan(layout: QueueLayout, edit: QueueEdit): QueueEditPlan {
        val original = layout.trackIds
        require(original.size <= PlaybackQueue.MAX_TRACKS &&
            original.size == original.toSet().size)
        require(layout.currentTrackId == null == original.isEmpty())
        if (original.isNotEmpty()) require(layout.currentTrackId in original)
        layout.shuffledIndices?.let { indices ->
            require(indices.size == original.size && indices.all { it in original.indices } &&
                indices.toSet().size == indices.size)
        }
        val requested = edit.ids.filter(String::isNotBlank).distinct()
        if (requested.isEmpty()) {
            return QueueEditPlan(layout, emptyList(), QueueEditResult(QueueEditStatus.EMPTY_SELECTION))
        }
        val current = layout.currentTrackId
        val already = original.toHashSet()
        val selected = mutableListOf<String>()
        var full = false
        var newCount = 0
        requested.forEach { id ->
            if (id == current) return@forEach
            if (id in already) selected.add(id)
            else if (original.size + newCount < PlaybackQueue.MAX_TRACKS) {
                selected.add(id)
                newCount++
            } else full = true
        }
        if (selected.isEmpty()) {
            val status = when {
                full -> QueueEditStatus.QUEUE_FULL
                current in requested -> QueueEditStatus.ALREADY_PLAYING
                else -> QueueEditStatus.EMPTY_SELECTION
            }
            return QueueEditPlan(layout, emptyList(), QueueEditResult(status))
        }
        val movedSet = selected.toHashSet()
        val remaining = layout.playbackIds().filterNot { it in movedSet }
        val insertion = if (edit is QueueEdit.PlayNext && current != null)
            remaining.indexOf(current) + 1 else remaining.size
        val playbackOrder = remaining.take(insertion) + selected + remaining.drop(insertion)
        val physical = if (layout.shuffleEnabled) {
            original + selected.filterNot { it in already }
        } else playbackOrder
        val physicalIndices = physical.withIndex().associate { it.value to it.index }
        val shuffle = if (layout.shuffleEnabled)
            playbackOrder.map { physicalIndices.getValue(it) }.toIntArray() else null
        val status = if (full) QueueEditStatus.QUEUE_FULL else QueueEditStatus.APPLIED
        return QueueEditPlan(
            QueueLayout(physical, shuffle, current ?: playbackOrder.first()),
            selected,
            QueueEditResult(status, selected.size),
        )
    }
}

/**
 * The source marker contains only a pre-existing account hash, never an email.
 * Version-1 unedited checkpoints remain unchanged and readable.
 */
object QueueEditSource {
    private const val PREFIX = "edited:"
    private val hashed = Regex("[a-f0-9]{64}")

    fun isValid(sourceKey: String): Boolean =
        hashed.matches(sourceKey) || (sourceKey.startsWith(PREFIX) &&
            hashed.matches(sourceKey.removePrefix(PREFIX)))

    fun edited(sourceKey: String): String {
        require(isValid(sourceKey))
        return if (sourceKey.startsWith(PREFIX)) sourceKey else PREFIX + sourceKey
    }

    fun belongsTo(sourceKey: String, accountKey: String): Boolean =
        sourceKey == accountKey || sourceKey == edited(accountKey)
}
