package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Synthetic queue only. The real caller reads nextMediaItemIndex from Media3,
 * which already decides order after shuffle/repeat.
 */
class NextPrefetchSelectionTest {
    private fun entry(id: String, size: Long = 4096L) = QueueTrack(
        id = id,
        title = "Synthetic title",
        encodedPath = "/AllMusic/synthetic_$id.mp3",
        remoteSize = size,
        remoteEtag = null,
    )

    @Test fun selectsAtMostActualNextFromResolvedMedia3Order() {
        val list = listOf(entry("first"), entry("second"), entry("third"))
        val byId = list.associateBy { it.id }
        assertEquals("third", NextPrefetchSelection.select(
            "first", "third", byId)?.id)
        assertEquals("second", NextPrefetchSelection.select(
            "first", "second", byId)?.id)
    }

    @Test fun neverPrefetchesSameRepeatOneOrMissingEnd() {
        val byId = listOf(entry("a"), entry("b")).associateBy { it.id }
        assertNull(NextPrefetchSelection.select("a", "a", byId))
        assertNull(NextPrefetchSelection.select("a", null, byId))
        assertNull(NextPrefetchSelection.select("a", "unknown", byId))
        assertNull(NextPrefetchSelection.select("a", "b",
            mapOf("b" to entry("b", 0L))))
    }

    @Test fun largeLibraryStillSelectsExactlyOne() {
        val byId = (0 until 3590).map { entry("id-$it") }.associateBy { it.id }
        assertEquals(3590, byId.size)
        assertEquals("id-3388", NextPrefetchSelection.select(
            "id-17", "id-3388", byId)?.id)
        assertNull(NextPrefetchSelection.select(
            "id-3589", null, byId))
    }
}
