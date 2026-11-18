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
    APPLIED, ALREADY_PLAYING, QUEUE_FULL, EMPTY_SELECTION, UNAVAILABLE, FAILED,
}

/** Number of selected non-current tracks actually relocated or added. */
data class QueueEditResult(val status: QueueEditStatus, val count: Int = 0)

data class QueueEditPlan(
    val layout: QueueLayout,
    val movedIds: List<String>,
    /** Previously played items removed from before the current track to make room. */
    val removedIds: List<String>,
    val result: QueueEditResult,
)

/**
 * Pure planner. A moved track occurs only once. Shuffle changes the traversal,
 * never randomly inserts new items among already scheduled songs.
 */
object QueueEditPlanner {
    fun plan(layout: QueueLayout, edit: QueueEdit): QueueEditPlan {
        validate(layout)
        val requested = edit.ids.filter(String::isNotBlank).distinct()
        val selected = selectIds(layout, requested)
        return when {
            requested.isEmpty() -> QueueEditPlan(
                layout, emptyList(), emptyList(), QueueEditResult(QueueEditStatus.EMPTY_SELECTION),
            )
            selected.ids.isEmpty() -> QueueEditPlan(
                layout, emptyList(), emptyList(), QueueEditResult(emptyResult(layout, requested, selected.full)),
            )
            else -> buildPlan(layout, edit, selected)
        }
    }

    private data class SelectedIds(val ids: List<String>, val removedIds: List<String>, val full: Boolean)

    private fun selectIds(layout: QueueLayout, requested: List<String>): SelectedIds {
        val original = layout.trackIds.toHashSet()
        val current = layout.currentTrackId
        val requestedIds = requested.filter { it != current }
        val movedExisting = requestedIds.filter { it in original }.toSet()
        val newRequested = requestedIds.filterNot { it in original }
        val playbackOrder = layout.playbackIds()
        val currentIndex = current?.let(playbackOrder::indexOf) ?: -1
        val alreadyPlayed = if (currentIndex > 0) playbackOrder.take(currentIndex)
            .filterNot { it in movedExisting } else emptyList()
        val free = (PlaybackQueue.MAX_TRACKS - layout.trackIds.size).coerceAtLeast(0)
        val evictCount = (newRequested.size - free).coerceAtLeast(0).coerceAtMost(alreadyPlayed.size)
        val removed = alreadyPlayed.take(evictCount)
        val acceptedNewCount = (free + removed.size).coerceAtMost(newRequested.size)
        val acceptedNew = newRequested.take(acceptedNewCount).toSet()
        val accepted = requestedIds.filter { it in original || it in acceptedNew }
        return SelectedIds(accepted, removed, accepted.size != requestedIds.size)
    }

    private fun emptyResult(
        layout: QueueLayout,
        requested: List<String>,
        full: Boolean,
    ): QueueEditStatus = when {
        full -> QueueEditStatus.QUEUE_FULL
        layout.currentTrackId in requested -> QueueEditStatus.ALREADY_PLAYING
        else -> QueueEditStatus.EMPTY_SELECTION
    }

    private fun buildPlan(
        layout: QueueLayout,
        edit: QueueEdit,
        selection: SelectedIds,
    ): QueueEditPlan {
        val movedSet = selection.ids.toHashSet()
        val removedSet = selection.removedIds.toHashSet()
        val remaining = layout.playbackIds().filterNot { it in movedSet || it in removedSet }
        val insertion = if (edit is QueueEdit.PlayNext && layout.currentTrackId != null)
            remaining.indexOf(layout.currentTrackId) + 1 else remaining.size
        val playbackOrder = remaining.take(insertion) + selection.ids + remaining.drop(insertion)
        val knownIds = layout.trackIds.toHashSet()
        val physical = if (layout.shuffleEnabled) {
            layout.trackIds.filterNot { it in removedSet } + selection.ids.filterNot { it in knownIds }
        } else playbackOrder
        val physicalIndices = physical.withIndex().associate { it.value to it.index }
        val shuffle = if (layout.shuffleEnabled)
            playbackOrder.map { physicalIndices.getValue(it) }.toIntArray() else null
        val status = if (selection.full) QueueEditStatus.QUEUE_FULL else QueueEditStatus.APPLIED
        return QueueEditPlan(
            QueueLayout(physical, shuffle, layout.currentTrackId ?: playbackOrder.first()),
            selection.ids,
            selection.removedIds,
            QueueEditResult(status, selection.ids.size),
        )
    }

    private fun validate(layout: QueueLayout) {
        val original = layout.trackIds
        require(original.size <= PlaybackQueue.MAX_TRACKS &&
            original.size == original.toSet().size)
        require((layout.currentTrackId == null) == original.isEmpty())
        if (original.isNotEmpty()) require(layout.currentTrackId in original)
        layout.shuffledIndices?.let { indices ->
            require(indices.size == original.size && indices.all { it in original.indices } &&
                indices.toSet().size == indices.size)
        }
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
