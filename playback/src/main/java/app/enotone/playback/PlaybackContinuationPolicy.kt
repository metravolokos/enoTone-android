package app.enotone.playback

import androidx.media3.common.Player

/**
 * A defensive recovery of an unexpected Media3 ENDED when next is available
 * or repeat-one is selected. Normal transitions do not need this; Pause and audio
 * focus suppression must never be turned into unsolicited playback.
 *
 * These inputs contain no media IDs, paths, or personal information.
 */
object PlaybackContinuationPolicy {
    fun shouldAutoRecover(
        ended: Boolean,
        playWhenReady: Boolean,
        hasNext: Boolean,
        repeatOne: Boolean,
        unsuppressed: Boolean,
    ): Boolean = ended && playWhenReady && (hasNext || repeatOne) && unsuppressed

    /**
     * Media3 обычно повторяет сам. Если всё-таки пришёл ENDED при ONE,
     * заново запускаем текущий item, сохраняя индекс очереди.
     */
    fun recoverUnexpectedEnd(player: Player): Boolean {
        if (!shouldAutoRecover(
                ended = player.playbackState == Player.STATE_ENDED,
                playWhenReady = player.playWhenReady,
                hasNext = player.hasNextMediaItem(),
                repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE,
                unsuppressed = player.playbackSuppressionReason ==
                    Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            )) return false

        if (player.repeatMode == Player.REPEAT_MODE_ONE) {
            player.seekToDefaultPosition()
        } else {
            player.seekToNextMediaItem()
        }
        if (player.playbackState == Player.STATE_ENDED ||
            player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
        return true
    }
}
