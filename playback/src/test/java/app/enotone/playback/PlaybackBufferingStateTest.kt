package app.enotone.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackBufferingStateTest {
    private fun cache(phase: CachePhase) = TransientCacheStatus(phase, 5L, 10L)

    private fun nextState(
        nextMediaItemIndex: Int = 0,
        repeatMode: Int = Player.REPEAT_MODE_OFF,
        phase: CachePhase = CachePhase.DOWNLOADING,
        currentCached: Boolean = true,
        queueCollected: Boolean = true,
    ) = resolveNextBufferState(
        nextMediaItemIndex = nextMediaItemIndex,
        repeatMode = repeatMode,
        nextCacheInfo = cache(phase),
        currentTrackFullyCached = currentCached,
        queueFullyCollected = queueCollected,
    )

    @Test fun noNextAtEndOfQueueIsNoneEvenWhenCacheStatusIsComplete() {
        assertEquals(NextBufferState.NONE, nextState(
            nextMediaItemIndex = C.INDEX_UNSET, phase = CachePhase.COMPLETE))
    }

    @Test fun repeatAllUsesMedia3ResolvedNextItem() {
        assertEquals(NextBufferState.READY, nextState(
            repeatMode = Player.REPEAT_MODE_ALL,
            phase = CachePhase.COMPLETE,
        ))
    }

    @Test fun repeatOneIsReadyOnlyAfterCurrentTrackIsFullyCached() {
        assertEquals(NextBufferState.READY, nextState(
            nextMediaItemIndex = C.INDEX_UNSET,
            repeatMode = Player.REPEAT_MODE_ONE,
            phase = CachePhase.IDLE,
            queueCollected = false,
        ))
        assertEquals(NextBufferState.NONE, nextState(
            repeatMode = Player.REPEAT_MODE_ONE,
            currentCached = false,
        ))
    }

    @Test fun shuffleResolvedNextIndexCanBeLoadingOrAbsent() {
        // The index supplied by Media3 is already in shuffle order.
        assertEquals(NextBufferState.LOADING, nextState(
            nextMediaItemIndex = 3, phase = CachePhase.DOWNLOADING))
        assertEquals(NextBufferState.NONE, nextState(
            nextMediaItemIndex = C.INDEX_UNSET, phase = CachePhase.DOWNLOADING))
    }

    @Test fun incompleteQueueDoesNotExposeAProvisionalNextItem() {
        assertEquals(NextBufferState.NONE, nextState(queueCollected = false))
    }

    @Test fun currentTrackMustFinishBeforeNextPrefetchIsShown() {
        assertEquals(NextBufferState.NONE, nextState(currentCached = false))
    }

    @Test fun nextDownloadPhasesMapToVisibleReadiness() {
        assertEquals(NextBufferState.LOADING, nextState(phase = CachePhase.DOWNLOADING))
        assertEquals(NextBufferState.READY, nextState(phase = CachePhase.COMPLETE))
        // A failed transfer stays in the incomplete/blinking style after a disconnect.
        assertEquals(NextBufferState.LOADING, nextState(phase = CachePhase.FAILED))
        assertEquals(NextBufferState.NONE, nextState(phase = CachePhase.SKIPPED))
        assertEquals(NextBufferState.NONE, nextState(phase = CachePhase.IDLE))
    }

    @Test fun queueChangeToUnscheduledCandidateReturnsToNone() {
        assertEquals(NextBufferState.NONE, nextState(phase = CachePhase.IDLE))
    }

    @Test fun bufferedPositionNeverFallsBehindPlayheadOrPastDuration() {
        assertEquals(300L, resolveBufferedPositionMs(
            positionMs = 300L,
            durationMs = 1_000L,
            playerBufferedPositionMs = 250L,
            currentTrackFullyCached = false,
        ))
        assertEquals(1_000L, resolveBufferedPositionMs(
            positionMs = 300L,
            durationMs = 1_000L,
            playerBufferedPositionMs = 1_200L,
            currentTrackFullyCached = false,
        ))
        assertEquals(1_000L, resolveBufferedPositionMs(
            positionMs = 1_200L,
            durationMs = 1_000L,
            playerBufferedPositionMs = 500L,
            currentTrackFullyCached = false,
        ))
    }

    @Test fun completedCurrentCacheBuffersToEndAndUnknownDurationIsSafe() {
        assertEquals(1_000L, resolveBufferedPositionMs(
            positionMs = 300L,
            durationMs = 1_000L,
            playerBufferedPositionMs = 350L,
            currentTrackFullyCached = true,
        ))
        assertEquals(0L, resolveBufferedPositionMs(
            positionMs = 300L,
            durationMs = 0L,
            playerBufferedPositionMs = 500L,
            currentTrackFullyCached = false,
        ))
    }
}
