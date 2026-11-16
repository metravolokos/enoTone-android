package app.enotone.playback

import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * Pure policy for the next-item indicator.
 *
 * `nextMediaItemIndex` is Media3's resolved decision, so it already accounts
 * for shuffle and repeat-all. The next-prefetch downloader waits until the
 * Media3 timeline reaches its `expectedQueueSize`.
 * This caller supplies the same gate as `loadedQueueSize >= queueSize`:
 * while a staged playlist is filling, `loadedQueueSize` is its materialized
 * item count; once staging finishes it is `player.mediaItemCount`. The values
 * are equivalent across both phases, so neither path exposes a provisional
 * next item.
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
 * authoritative source of a fully buffered current item. When duration is
 * unknown (`durationMs == 0`), the result is zero: there is no valid timeline
 * range to report, even if the saved playhead or Media3 buffer is non-zero.
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

/** Lazily reads Media3's buffer only while the Now Playing screen is visible. */
fun resolveVisiblePlayerBufferPositionMs(
    nowPlayingVisible: Boolean,
    positionMs: Long,
    readPlayerBufferedPositionMs: () -> Long,
): Long = if (nowPlayingVisible) readPlayerBufferedPositionMs() else positionMs
