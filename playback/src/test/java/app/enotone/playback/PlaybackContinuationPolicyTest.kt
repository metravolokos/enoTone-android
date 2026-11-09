package app.enotone.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackContinuationPolicyTest {
    @Test fun recoversOnlyUnexpectedEndWithRequestedPlaybackAndNextItem() {
        assertTrue(PlaybackContinuationPolicy.shouldAutoRecover(
            ended = true, playWhenReady = true, hasNext = true,
            repeatOne = false, unsuppressed = true))
        assertFalse(PlaybackContinuationPolicy.shouldAutoRecover(
            ended = false, playWhenReady = true, hasNext = true,
            repeatOne = false, unsuppressed = true))
        assertFalse(PlaybackContinuationPolicy.shouldAutoRecover(
            ended = true, playWhenReady = true, hasNext = false,
            repeatOne = false, unsuppressed = true))
    }

    @Test fun neverOverridesExplicitPauseFocusLossOrRepeatOne() {
        assertFalse(PlaybackContinuationPolicy.shouldAutoRecover(
            ended = true, playWhenReady = false, hasNext = true,
            repeatOne = false, unsuppressed = true))
        assertFalse(PlaybackContinuationPolicy.shouldAutoRecover(
            ended = true, playWhenReady = true, hasNext = true,
            repeatOne = false, unsuppressed = false))
        assertFalse(PlaybackContinuationPolicy.shouldAutoRecover(
            ended = true, playWhenReady = true, hasNext = true,
            repeatOne = true, unsuppressed = true))
    }
}
