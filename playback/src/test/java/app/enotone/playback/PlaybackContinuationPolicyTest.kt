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

    @Test fun neverOverridesExplicitPauseOrFocusLoss() {
        assertFalse(PlaybackContinuationPolicy.shouldAutoRecover(
            ended = true, playWhenReady = false, hasNext = true,
            repeatOne = false, unsuppressed = true))
        assertFalse(PlaybackContinuationPolicy.shouldAutoRecover(
            ended = true, playWhenReady = true, hasNext = true,
            repeatOne = false, unsuppressed = false))
    }

    @Test fun repeatOneMustRecoverUnexpectedEndEvenForSingleTrackQueue() {
        // Если Media3 неожиданно дошёл до ENDED в режиме ONE, текущий
        // трек должен запускаться заново независимо от наличия Next.
        for (hasNext in listOf(false, true)) {
            assertTrue(PlaybackContinuationPolicy.shouldAutoRecover(
                ended = true, playWhenReady = true, hasNext = hasNext,
                repeatOne = true, unsuppressed = true))
        }
    }
}
