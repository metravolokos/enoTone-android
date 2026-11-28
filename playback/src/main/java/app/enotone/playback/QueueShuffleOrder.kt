package app.enotone.playback

import kotlin.random.Random

/**
 * Builds the full playback permutation without letting Media3 choose a different
 * first item. The selected/current physical index stays first; every other index is
 * shuffled exactly once with a reproducible Fisher–Yates pass.
 */
internal object QueueShuffleOrder {
    fun create(size: Int, firstIndex: Int, seed: Long): IntArray {
        require(size > 0) { "Queue must not be empty" }
        require(firstIndex in 0 until size) { "First index must be present" }
        if (size == 1) return intArrayOf(firstIndex)

        val remaining = IntArray(size - 1)
        var cursor = 0
        for (index in 0 until size) {
            if (index != firstIndex) remaining[cursor++] = index
        }
        val random = Random(seed)
        for (index in remaining.lastIndex downTo 1) {
            val swapWith = random.nextInt(index + 1)
            val previous = remaining[index]
            remaining[index] = remaining[swapWith]
            remaining[swapWith] = previous
        }
        return intArrayOf(firstIndex) + remaining
    }
}
