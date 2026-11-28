package app.enotone.playback

/**
 * Preserve the entire local queue while limiting the first synchronous Media3
 * playlist to the selected track and two neighbors on either side.
 *
 * Later chunks are inserted at their physical-order position. A requested target
 * outside the loaded slice can be materialized as one bounded chunk instead of
 * waiting for the whole after-tail or preceding history. The plan tracks the
 * temporary sparse timeline until every original index is loaded.
 *
 * Pure Kotlin, no I/O and no network activity.
 */
class StagedPlaylistPlan private constructor(
    val items: List<QueueTrack>,
    val selectedIndex: Int,
) {
    data class Chunk(
        val items: List<QueueTrack>,
        val prepend: Boolean,
        val insertionIndex: Int,
    )

    val seedStart: Int = (selectedIndex - SEED_SIDE_COUNT).coerceAtLeast(0)
    val seedEndExclusive: Int =
        (selectedIndex + SEED_SIDE_COUNT + 1).coerceAtMost(items.size)
    val seed: List<QueueTrack> = items.subList(seedStart, seedEndExclusive)
    val seedSelectedIndex: Int = selectedIndex - seedStart

    private val loaded = BooleanArray(items.size)
    private val timelineOrder = ArrayList<Int>(items.size)
    private var loadedCountValue = seed.size

    init {
        for (index in seedStart until seedEndExclusive) {
            loaded[index] = true
            timelineOrder.add(index)
        }
    }

    val loadedCount: Int get() = loadedCountValue

    /** The loaded timeline is contiguous only on the original staging path. */
    val isContiguous: Boolean
        get() = timelineOrder.indices.all { timelineOrder[it] == timelineOrder.first() + it }

    val loadedStartIndex: Int get() = timelineOrder.first()
    val loadedEndExclusive: Int get() = timelineOrder.last() + 1

    val isComplete: Boolean get() = loadedCountValue == items.size

    fun isLoaded(originalIndex: Int): Boolean =
        originalIndex in items.indices && loaded[originalIndex]

    fun timelineIndex(originalIndex: Int): Int? =
        originalIndex.takeIf { it in items.indices && loaded[it] }
            ?.let(timelineOrder::indexOf)

    /** Normal fill: playable after-tail first, then preceding history. */
    fun nextChunk(): Chunk? = nextChunkInternal(maxItems = CHUNK_SIZE, targetIndex = null)

    /** Load a bounded physical chunk ending at the requested target. */
    fun nextChunk(targetIndex: Int): Chunk? {
        require(targetIndex in items.indices) { "Target index outside queue" }
        if (loaded[targetIndex]) return null
        var start = targetIndex
        while (start > 0 && targetIndex - start + 1 < CHUNK_SIZE && !loaded[start - 1]) {
            start--
        }
        return takeChunk(start..targetIndex)
    }

    /** Finish a contiguous missing gap in one operation before a queue edit. */
    fun nextGap(): Chunk? = nextChunkInternal(maxItems = Int.MAX_VALUE, targetIndex = null)

    private fun nextChunkInternal(maxItems: Int, targetIndex: Int?): Chunk? {
        if (targetIndex != null) return nextChunk(targetIndex)
        return nextNormalRange(maxItems)?.let(::takeChunk)
    }

    private fun nextNormalRange(maxItems: Int): IntRange? {
        val afterStart = (seedEndExclusive until items.size).firstOrNull { !loaded[it] }
        val beforeEnd = (seedStart - 1 downTo 0).firstOrNull { !loaded[it] }
        return when {
            afterStart != null -> {
                var end = afterStart + 1
                while (end < items.size && end - afterStart < maxItems && !loaded[end]) end++
                afterStart until end
            }
            beforeEnd != null -> {
                var start = beforeEnd
                while (start > 0 && beforeEnd - start + 1 < maxItems && !loaded[start - 1]) start--
                start..beforeEnd
            }
            else -> null
        }
    }

    private fun takeChunk(range: IntRange): Chunk {
        val insertionIndex = insertionIndexFor(range)
        val chunk = Chunk(
            items = range.map(items::get),
            prepend = insertionIndex == 0,
            insertionIndex = insertionIndex,
        )
        timelineOrder.addAll(insertionIndex, range.toList())
        for (index in range) loaded[index] = true
        loadedCountValue += range.count()
        return chunk
    }

    private fun insertionIndexFor(range: IntRange): Int {
        val nextLoaded = (range.last + 1 until items.size).firstOrNull { loaded[it] }
        if (nextLoaded != null) return timelineOrder.indexOf(nextLoaded)
        val previousLoaded = (range.first - 1 downTo 0).firstOrNull { loaded[it] }
        return if (previousLoaded == null) 0 else timelineOrder.indexOf(previousLoaded) + 1
    }

    companion object {
        const val SEED_SIDE_COUNT = 2
        const val CHUNK_SIZE = 32

        fun create(queue: PlaybackQueue): StagedPlaylistPlan =
            StagedPlaylistPlan(queue.items, queue.position)
    }
}
