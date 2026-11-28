package app.enotone.playback

import androidx.media3.common.Player

/**
 * Владелец 2026-11-15: Play после ошибки идёт к следующему треку; если
 * переходить некуда или включён повтор одного — текущий с начала.
 * Ошибку очищаем до prepare(), иначе он не сбросит состояние.
 */
internal fun PreviewAudioPlayback.recoverAfterError() {
    val currentTrackId = player.currentMediaItem?.mediaId
    val logicalNext = queueNavigation.adjacentPosition(
        currentTrackId,
        forward = true,
        repeatAll = player.repeatMode == Player.REPEAT_MODE_ALL,
    )
    val hasNext = when (logicalNext) {
        null -> player.hasNextMediaItem()
        PlaybackQueueNavigation.NO_POSITION -> false
        else -> true
    }
    val action = PlaybackErrorRecoveryPolicy.actionFor(
        hasError = true,
        hasNext = hasNext,
        repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE,
    )
    deferredPrepare = false
    when (action) {
        PlaybackErrorAction.NEXT -> if (logicalNext != null &&
            logicalNext != PlaybackQueueNavigation.NO_POSITION
        ) {
            seekToLogicalPositionAfterError(logicalNext, currentTrackId)
        } else {
            player.seekToNextMediaItem()
        }
        PlaybackErrorAction.RESTART_CURRENT -> player.seekToDefaultPosition()
        PlaybackErrorAction.PLAY -> Unit
    }
    player.prepare()
    player.play()
}

/** Error recovery seeks without resuming until the player error has been cleared. */
private fun PreviewAudioPlayback.seekToLogicalPositionAfterError(
    position: Int,
    currentTrackId: String?,
) {
    queueNavigation.clearPendingSeek()
    if (position == currentTrackId?.let(queueNavigation::positionOf)) {
        player.seekToDefaultPosition()
        return
    }
    queueNavigation.selectPosition(
        position = position,
        currentTrackId = currentTrackId,
        seekLoadedTarget = { targetId ->
            queueTimelineIndex(targetId)?.let { timelineIndex ->
                player.seekToDefaultPosition(timelineIndex)
                true
            } ?: false
        },
        stageMoreItems = ::stageMoreQueueItems,
    )
}
