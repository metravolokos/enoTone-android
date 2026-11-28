package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackQueueSnapshotTest {
    @Test fun acceptsTenThousandTracksAndRejectsTenThousandAndOne() {
        val ids = (0 until 10_000).map { "synthetic-$it" }
        assertEquals(10_000, PlaybackQueueSnapshot(ids, currentIndex = 9_999).trackIds.size)
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueueSnapshot(ids + "overflow", currentIndex = 0)
        }
    }
}
