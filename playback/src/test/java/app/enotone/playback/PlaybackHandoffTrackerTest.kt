package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Pure numeric fixtures. No account, file or track identifiers are stored. */
class PlaybackHandoffTrackerTest {
    @Test fun excludesInitialPlaybackAndCapturesCachedNextAfterButtonPress() {
        val tracker = PlaybackHandoffTracker()
        tracker.startNewQueue()
        tracker.onItemChanged(automatic = false, preparedNext = false, nowMs = 10)
        tracker.onReady(40)
        assertNull(tracker.snapshot())
        tracker.beginButtonSkip(HandoffOrigin.NEXT_BUTTON, 100)
        tracker.onItemChanged(automatic = false, preparedNext = true, nowMs = 115)
        tracker.onBuffering(116)
        tracker.onReady(160)
        tracker.onPlaying(161)
        assertEquals(HandoffSnapshot(
            origin = HandoffOrigin.NEXT_BUTTON,
            phase = HandoffPhase.READY,
            preparedNext = true,
            bufferingMs = 16L,
            readyMs = 60L,
            playingMs = 61L,
        ), tracker.snapshot())
    }

    @Test fun tracksAutoTransitionWithoutAssumingGapless() {
        val tracker = PlaybackHandoffTracker()
        tracker.onItemChanged(false, false, 1)
        tracker.onItemChanged(automatic = true, preparedNext = false, nowMs = 120)
        tracker.onPlaying(133)
        tracker.onReady(145)
        assertEquals(HandoffOrigin.AUTO, tracker.snapshot()?.origin)
        assertEquals(25L, tracker.snapshot()?.readyMs)
        assertEquals(13L, tracker.snapshot()?.playingMs)
        assertEquals(null, tracker.snapshot()?.bufferingMs)
    }

    @Test fun tracksHeadsetMediaSessionAndErrors() {
        val tracker = PlaybackHandoffTracker()
        tracker.onItemChanged(false, false, 0)
        tracker.onItemChanged(false, false, 200)
        tracker.onBuffering(208)
        tracker.onError()
        assertEquals(HandoffOrigin.MEDIA_SESSION, tracker.snapshot()?.origin)
        assertEquals(HandoffPhase.FAILED, tracker.snapshot()?.phase)
        assertEquals(8L, tracker.snapshot()?.bufferingMs)
        tracker.stop()
        assertNull(tracker.snapshot())
    }

    @Test fun neverMisattributesOldQueueOrAcceptsInvalidManualCommand() {
        val tracker = PlaybackHandoffTracker()
        assertThrows(IllegalArgumentException::class.java) {
            tracker.beginButtonSkip(HandoffOrigin.AUTO, 20)
        }
        tracker.onItemChanged(false, false, 10)
        tracker.beginButtonSkip(HandoffOrigin.PREVIOUS_BUTTON, 30)
        tracker.startNewQueue()
        tracker.onItemChanged(false, false, 35)
        assertNull(tracker.snapshot())
        tracker.onItemChanged(false, false, 50)
        assertEquals(HandoffOrigin.MEDIA_SESSION, tracker.snapshot()?.origin)
    }
}
