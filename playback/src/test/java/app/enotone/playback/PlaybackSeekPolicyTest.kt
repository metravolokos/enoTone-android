package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSeekPolicyTest {
    @Test fun neverSeekWhenDurationIsUnknownOrInputIsInvalid() {
        assertFalse(PlaybackSeekPolicy.isKnownDuration(0))
        assertFalse(PlaybackSeekPolicy.isKnownDuration(-1L))
        assertFalse(PlaybackSeekPolicy.isKnownDuration(Long.MIN_VALUE + 1L))
        assertNull(PlaybackSeekPolicy.positionForFraction(-1L, 0.5f))
        assertNull(PlaybackSeekPolicy.positionForFraction(0L, 0.5f))
        assertNull(PlaybackSeekPolicy.positionForFraction(150_000L, Float.NaN))
        assertNull(PlaybackSeekPolicy.positionForFraction(150_000L, Float.POSITIVE_INFINITY))
        assertNull(PlaybackSeekPolicy.positionForFraction(150_000L, Float.NEGATIVE_INFINITY))
        assertEquals(0f, PlaybackSeekPolicy.fraction(5_000, -1L), 0f)
    }

    @Test fun seekAlwaysStaysInKnownCurrentTrackBounds() {
        val duration = 120_000L
        assertTrue(PlaybackSeekPolicy.isKnownDuration(duration))
        assertEquals(0L, PlaybackSeekPolicy.positionForFraction(duration, -3f))
        assertEquals(0L, PlaybackSeekPolicy.positionForFraction(duration, 0f))
        assertEquals(30_000L, PlaybackSeekPolicy.positionForFraction(duration, 0.25f))
        assertEquals(60_000L, PlaybackSeekPolicy.positionForFraction(duration, 0.5f))
        assertEquals(duration, PlaybackSeekPolicy.positionForFraction(duration, 1f))
        assertEquals(duration, PlaybackSeekPolicy.positionForFraction(duration, 3f))
    }

    @Test fun playedFractionClampsStalePositions() {
        assertEquals(0f, PlaybackSeekPolicy.fraction(-10_000L, 60_000L), 0f)
        assertEquals(0.5f, PlaybackSeekPolicy.fraction(30_000L, 60_000L), 0f)
        assertEquals(1f, PlaybackSeekPolicy.fraction(90_000L, 60_000L), 0f)
        assertEquals(0f, PlaybackSeekPolicy.fraction(0L, 60_000L), 0f)
    }

    @Test fun elapsedTimeSupportsHoursAndUnknownValues() {
        assertEquals("--:--", PlaybackSeekPolicy.formatTime(-1L))
        assertEquals("00:00", PlaybackSeekPolicy.formatTime(0L))
        assertEquals("00:09", PlaybackSeekPolicy.formatTime(9_950L))
        assertEquals("02:03", PlaybackSeekPolicy.formatTime(123_000L))
        assertEquals("01:01:01", PlaybackSeekPolicy.formatTime(3_661_000L))
        assertEquals("27:00:00", PlaybackSeekPolicy.formatTime(27L * 3600L * 1000L))
    }

    @Test fun seekDiscontinuitiesSavePositionButPlaybackProgressDoesNot() {
        assertTrue(PlaybackSeekPolicy.isUserSeek(androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK))
        assertTrue(PlaybackSeekPolicy.isUserSeek(androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT))
        assertFalse(PlaybackSeekPolicy.isUserSeek(androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION))
        assertFalse(PlaybackSeekPolicy.isUserSeek(androidx.media3.common.Player.DISCONTINUITY_REASON_REMOVE))
    }
}
