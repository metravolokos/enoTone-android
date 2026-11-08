package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only synthetic titles and paths; no real media-library data in public tests. */
class PlaybackQueueTest {
    private fun track(n: Int) = QueueTrack(
        id = "id-$n", title = "Synthetic $n",
        encodedPath = "/AllMusic/synthetic_$n.mp3",
        remoteSize = 1024L, remoteEtag = null,
    )

    @Test fun playsInVisibleSearchOrderAndStopsAtTheEnd() {
        val queue = PlaybackQueue.create(listOf(track(8), track(3), track(5)), "id-3")
        assertEquals(3, queue.size)
        assertEquals(1, queue.position)
        assertEquals("id-3", queue.current.id)
        assertTrue(queue.hasPrevious)
        assertTrue(queue.hasNext)
        assertEquals("id-5", queue.next()?.id)
        assertFalse(queue.hasNext)
        assertEquals(null, queue.next())
        assertEquals("id-5", queue.current.id)
        assertEquals("id-3", queue.previous()?.id)
        assertEquals("id-8", queue.previous()?.id)
        assertFalse(queue.hasPrevious)
        assertEquals(null, queue.previous())
    }

    @Test fun rejectsMissingSelectionDuplicatesAndUnplayableRows() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueue.create(emptyList(), "id-1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueue.create(listOf(track(1)), "id-2")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueue.create(listOf(track(1), track(1)), "id-1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueue.create(listOf(track(1).copy(remoteSize = 0L)), "id-1")
        }
    }

    @Test fun holds3590EntriesWithoutNetworkOrFormatSpecificLogic() {
        val items = (0 until 3590).map(::track)
        val queue = PlaybackQueue.create(items, "id-3500")
        assertEquals(3590, queue.size)
        assertEquals(3500, queue.position)
        assertEquals("id-3501", queue.next()?.id)
    }
}
