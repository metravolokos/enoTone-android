package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackReadWindowTest {
    @Test fun exactEofIsLegalWithoutAnyNetworkRange() {
        val end = PlaybackReadWindow.resolve(1_000L, 1_000L, -1L)!!
        assertEquals(0L, end.bytesAvailable)
        assertNull(end.endInclusive)
        assertEquals(0L, end.openReturnLength)
        val bounded = PlaybackReadWindow.resolve(1_000L, 1_000L, 128L)!!
        assertEquals(0L, bounded.bytesAvailable)
        assertNull(bounded.endInclusive)
        assertEquals(128L, bounded.openReturnLength)
    }

    @Test fun allNormalAndBoundedReadsRetainExactRanges() {
        val entire = PlaybackReadWindow.resolve(1_000L, 0L, -1L)!!
        assertEquals(1_000L, entire.bytesAvailable)
        assertEquals(999L, entire.endInclusive)
        val middle = PlaybackReadWindow.resolve(1_000L, 200L, 500L)!!
        assertEquals(500L, middle.bytesAvailable)
        assertEquals(699L, middle.endInclusive)
        val clamped = PlaybackReadWindow.resolve(1_000L, 900L, 200L)!!
        assertEquals(100L, clamped.bytesAvailable)
        assertEquals(999L, clamped.endInclusive)
        // Media3 open() returns the explicit requested length even when
        // fewer actual bytes remain and read() later returns EOF.
        assertEquals(200L, clamped.openReturnLength)
    }

    @Test fun beyondEofRemainsRejectedAndNumbersAreValidated() {
        assertNull(PlaybackReadWindow.resolve(1_000L, 1_001L, -1L))
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackReadWindow.resolve(0L, 0L, -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackReadWindow.resolve(100L, -1L, -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackReadWindow.resolve(100L, 0L, 0L)
        }
    }
}
