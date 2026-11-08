package app.enotone.playback

/**
 * Preserve the entire local queue while limiting the first synchronous Media3
 * playlist to the selected track and two neighbors on either side.
 *
 * Items after the seed are appended in order. Earlier items are subsequently
 * prepended in reverse chunk order, so the final Media3 timeline is exactly
 * [items] with the originally selected mediaId still playing.
 *
 * Pure Kotlin, no I/O and no network activity.
 */
class StagedPlaylistPlan private constructor(
    val items: List<QueueTrack>,
    val selectedIndex: Int,
) {
    data class Chunk(val items: List<QueueTrack>, val prepend: Boolean)

    val seedStart: Int = (selectedIndex - SEED_SIDE_COUNT).coerceAtLeast(0)
    val seedEndExclusive: Int =
        (selectedIndex + SEED_SIDE_COUNT + 1).coerceAtMost(items.size)
    val seed: List<QueueTrack> = items.subList(seedStart, seedEndExclusive)
    val seedSelectedIndex: Int = selectedIndex - seedStart
    private var nextAfter: Int = seedEndExclusive
    private var nextBeforeEnd: Int = seedStart

    val loadedCount: Int
        get() = seed.size + (nextAfter - seedEndExclusive) +
            (seedStart - nextBeforeEnd)

    val isComplete: Boolean
        get() = nextAfter == items.size && nextBeforeEnd == 0

    fun nextChunk(): Chunk? {
        // Prioritize playable next tracks before filling preceding history.
        if (nextAfter < items.size) {
            val end = (nextAfter + CHUNK_SIZE).coerceAtMost(items.size)
            val chunk = Chunk(items.subList(nextAfter, end), prepend = false)
            nextAfter = end
            return chunk
        }
        if (nextBeforeEnd > 0) {
            val start = (nextBeforeEnd - CHUNK_SIZE).coerceAtLeast(0)
            val chunk = Chunk(items.subList(start, nextBeforeEnd), prepend = true)
            nextBeforeEnd = start
            return chunk
        }
        return null
    }

    companion object {
        const val SEED_SIDE_COUNT = 2
        const val CHUNK_SIZE = 32

        fun create(queue: PlaybackQueue): StagedPlaylistPlan =
            StagedPlaylistPlan(queue.items, queue.position)
    }
}
