package app.enotone.playback

import androidx.media3.common.C
import androidx.media3.common.Player

/** Re-prepares only the failed item and restores its last position before playback resumes. */
internal object PlaybackErrorRetry {
    fun retryCurrent(player: Player, clearDeferredPrepare: () -> Unit): Boolean {
        val mediaItemIndex = player.currentMediaItemIndex
        val canRetry = player.playerError != null && mediaItemIndex != C.INDEX_UNSET &&
            player.currentMediaItem != null
        if (canRetry) {
            val positionMs = player.currentPosition.coerceAtLeast(0L)
            clearDeferredPrepare()
            player.prepare()
            player.seekTo(mediaItemIndex, positionMs)
            player.play()
        }
        return canRetry
    }
}
