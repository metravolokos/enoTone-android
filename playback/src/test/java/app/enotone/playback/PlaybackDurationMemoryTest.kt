package app.enotone.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDurationMemoryTest {
    @Test fun knownDurationSurvivesTransientUnsetForTheSameMediaItem() {
        val memory = PlaybackDurationMemory()

        assertEquals(120_000L, memory.resolve("track-a", 120_000L))
        assertEquals(120_000L, memory.resolve("track-a", C.TIME_UNSET))
        assertEquals(120_000L, memory.resolve("track-a", 0L))
    }

    @Test fun durationNeverLeaksIntoAnotherMediaItem() {
        val memory = PlaybackDurationMemory()
        memory.resolve("track-a", 120_000L)

        assertEquals(0L, memory.resolve("track-b", C.TIME_UNSET))
        assertEquals(0L, memory.resolve(null, C.TIME_UNSET))
        assertEquals(120_000L, memory.resolve("track-a", C.TIME_UNSET))
    }

    @Test fun newerRawDurationReplacesTheFallback() {
        val memory = PlaybackDurationMemory()
        memory.resolve("track-a", 120_000L)

        assertEquals(180_000L, memory.resolve("track-a", 180_000L))
        assertEquals(180_000L, memory.resolve("track-a", C.TIME_UNSET))
    }

    @Test fun restoredFallbackIsScopedToTheSelectedTrackAndIgnoredWhenUnknown() {
        val memory = PlaybackDurationMemory()
        memory.remember("track-a", 0L)
        assertEquals(0L, memory.resolve("track-a", C.TIME_UNSET))

        memory.remember("track-a", 228_000L)
        assertEquals(228_000L, memory.resolve("track-a", C.TIME_UNSET))
        assertEquals(0L, memory.resolve("track-b", C.TIME_UNSET))
    }

    @Test fun clearDropsTheFallbackForANewQueueOrStop() {
        val memory = PlaybackDurationMemory()
        memory.resolve("track-a", 120_000L)

        memory.clear()

        assertEquals(0L, memory.resolve("track-a", C.TIME_UNSET))
    }
}
