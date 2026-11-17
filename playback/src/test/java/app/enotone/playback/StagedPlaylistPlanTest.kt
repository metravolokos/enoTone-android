package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Strictly synthetic IDs and paths: do not publish real library contents. */
class StagedPlaylistPlanTest {
    private fun item(n: Int) = QueueTrack(
        id = "synthetic-$n", title = "Synthetic $n",
        encodedPath = "/AllMusic/synthetic_$n.mp3",
        remoteSize = 4096L, remoteEtag = null,
    )

    private fun checkReconstruction(size: Int, selected: Int) {
        val originals = (0 until size).map(::item)
        val queue = PlaybackQueue.create(originals, "synthetic-$selected")
        val plan = StagedPlaylistPlan.create(queue)
        assertEquals(selected, plan.seed[plan.seedSelectedIndex].id.removePrefix("synthetic-").toInt())
        assertTrue(plan.seed.size <= 5)
        val rebuilt = plan.seed.toMutableList()
        while (!plan.isComplete) {
            val chunk = plan.nextChunk() ?: throw AssertionError("Missing chunk")
            assertTrue(chunk.items.isNotEmpty())
            assertTrue(chunk.items.size <= StagedPlaylistPlan.CHUNK_SIZE)
            if (chunk.prepend) rebuilt.addAll(0, chunk.items)
            else rebuilt.addAll(chunk.items)
            assertEquals(rebuilt.size, plan.loadedCount)
            val loadedStart = rebuilt.first().id.removePrefix("synthetic-").toInt()
            assertEquals(loadedStart, plan.loadedStartIndex)
            assertEquals(loadedStart + rebuilt.size, plan.loadedEndExclusive)
        }
        assertEquals(originals, rebuilt)
        assertEquals(size, plan.loadedCount)
        assertNull(plan.nextChunk())
    }

    @Test fun maintainsFullLibraryOrderAtDifferentSelectionPositions() {
        for (selected in listOf(0, 1, 2, 50, 97, 98, 99)) {
            checkReconstruction(100, selected)
        }
    }

    @Test fun retainsOrderForShortFilteredContexts() {
        for (size in 1..7) for (selected in 0 until size) {
            checkReconstruction(size, selected)
        }
    }

    @Test fun seedKeepsNativeNextAndPreviousImmediately() {
        val originals = (0 until 100).map(::item)
        val plan = StagedPlaylistPlan.create(
            PlaybackQueue.create(originals, "synthetic-50"))
        assertEquals(5, plan.seed.size)
        assertEquals(2, plan.seedSelectedIndex)
        assertEquals("synthetic-49", plan.seed[1].id)
        assertEquals("synthetic-51", plan.seed[3].id)
        assertFalse(plan.isComplete)
        val firstChunk = plan.nextChunk()
        assertEquals(false, firstChunk?.prepend)
        assertEquals("synthetic-53", firstChunk?.items?.first()?.id)
    }
}
