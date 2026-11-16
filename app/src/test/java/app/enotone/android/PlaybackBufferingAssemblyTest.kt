package app.enotone.android

import androidx.media3.common.C
import androidx.media3.common.Player
import app.enotone.playback.CachePhase
import app.enotone.playback.NextBufferState
import app.enotone.playback.TransientCacheStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBufferingAssemblyTest {
    private fun cache(phase: CachePhase) = TransientCacheStatus(phase, 5L, 10L)

    @Test fun visibleSnapshotAssemblesBufferAndResolvedNextState() {
        var reads = 0
        val snapshot = resolvePlaybackBufferingSnapshot(
            positionMs = 86_000L,
            durationMs = 228_000L,
            knownDurationMs = 228_000L,
            nowPlayingVisible = true,
            readPlayerBufferedPositionMs = { reads++; 146_000L },
            currentTrackFullyCached = true,
            repeatMode = Player.REPEAT_MODE_ALL,
            nextMediaItemIndex = 4,
            nextCacheInfo = cache(CachePhase.COMPLETE),
            queueFullyCollected = true,
        )

        assertEquals(1, reads)
        assertEquals(228_000L, snapshot.bufferedPositionMs)
        assertEquals(NextBufferState.READY, snapshot.nextBufferState)
        assertTrue(snapshot.hasNext)
    }

    @Test fun hiddenSnapshotSkipsPlayerBufferReadAndUnknownDurationHasNoRange() {
        var reads = 0
        val snapshot = resolvePlaybackBufferingSnapshot(
            positionMs = 86_000L,
            durationMs = 0L,
            knownDurationMs = null,
            nowPlayingVisible = false,
            readPlayerBufferedPositionMs = { reads++; 146_000L },
            currentTrackFullyCached = false,
            repeatMode = Player.REPEAT_MODE_OFF,
            nextMediaItemIndex = C.INDEX_UNSET,
            nextCacheInfo = cache(CachePhase.DOWNLOADING),
            queueFullyCollected = false,
        )

        assertEquals(0, reads)
        assertEquals(0L, snapshot.bufferedPositionMs)
        assertEquals(NextBufferState.NONE, snapshot.nextBufferState)
        assertFalse(snapshot.hasNext)
    }

    @Test fun provisionalStagedQueueCannotExposeNextReadiness() {
        val snapshot = resolvePlaybackBufferingSnapshot(
            positionMs = 1_000L,
            durationMs = 10_000L,
            knownDurationMs = 10_000L,
            nowPlayingVisible = true,
            readPlayerBufferedPositionMs = { 4_000L },
            currentTrackFullyCached = true,
            repeatMode = Player.REPEAT_MODE_OFF,
            nextMediaItemIndex = 1,
            nextCacheInfo = cache(CachePhase.COMPLETE),
            queueFullyCollected = false,
        )

        assertEquals(NextBufferState.NONE, snapshot.nextBufferState)
    }
}
