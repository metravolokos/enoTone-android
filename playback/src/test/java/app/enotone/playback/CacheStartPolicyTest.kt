package app.enotone.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit contract: preserve fast first sound, then independent cache download. */
class CacheStartPolicyTest {
    @Test fun neverStartFullDownloadBeforeFirstAudioBufferReady() {
        for (state in listOf(
            Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_ENDED,
        )) {
            assertFalse(CacheStartPolicy.mayStart(state, "current", "current"))
        }
    }

    @Test fun onlyCurrentSelectedTrackMayStart() {
        assertTrue(CacheStartPolicy.mayStart(
            Player.STATE_READY, "current", "current"))
        assertFalse(CacheStartPolicy.mayStart(
            Player.STATE_READY, "other", "current"))
        assertFalse(CacheStartPolicy.mayStart(
            Player.STATE_READY, "current", null))
        assertTrue(CacheStartPolicy.START_DELAY_MS >= 500L)
    }

    @Test fun readinessDoesNotDependOnPlayWhenReady() {
        // No playWhenReady is part of the gate's API: Pause preserves a pending
        // full-track cache download once the track has reached READY.
        assertTrue(CacheStartPolicy.mayStart(
            Player.STATE_READY, "paused-selected-track", "paused-selected-track"))
    }
}
