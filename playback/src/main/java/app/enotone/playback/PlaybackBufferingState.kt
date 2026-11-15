package app.enotone.playback

import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * Pure policy for the next-item indicator.
 *
 * `nextMediaItemIndex` is Media3's resolved decision, so it already accounts
 * for shuffle and repeat-all. The current cache and queue gates mirror the
 * downloader: it cannot choose a stable next item until both are complete.
 */
fun resolveNextBufferState(
    nextMediaItemIndex: Int,
    repeatMode: Int,
    nextCacheInfo: TransientCacheStatus,
    currentTrackFullyCached: Boolean,
    queueFullyCollected: Boolean,
): NextBufferState {
    return when {
        repeatMode == Player.REPEAT_MODE_ONE && currentTrackFullyCached -> NextBufferState.READY
        repeatMode == Player.REPEAT_MODE_ONE -> NextBufferState.NONE
        nextMediaItemIndex == C.INDEX_UNSET || !currentTrackFullyCached || !queueFullyCollected ->
            NextBufferState.NONE
        else -> when (nextCacheInfo.phase) {
            CachePhase.DOWNLOADING, CachePhase.FAILED -> NextBufferState.LOADING
            CachePhase.COMPLETE -> NextBufferState.READY
            CachePhase.IDLE, CachePhase.SKIPPED -> NextBufferState.NONE
        }
    }
}

/**
 * Clamps Media3's buffered position to the displayed duration and never lets
 * the buffer fall behind the playhead. A completed full-track cache is the
 * authoritative source of a fully buffered current item.
 */
fun resolveBufferedPositionMs(
    positionMs: Long,
    durationMs: Long,
    playerBufferedPositionMs: Long,
    currentTrackFullyCached: Boolean,
): Long {
    val safeDuration = durationMs.coerceAtLeast(0L)
    if (currentTrackFullyCached) return safeDuration

    // Keep the lower bound valid when a stale saved position exceeds duration.
    val safePosition = positionMs.coerceIn(0L, safeDuration)
    return playerBufferedPositionMs.coerceIn(safePosition, safeDuration)
}
