package app.enotone.playback

import androidx.media3.common.Player

/** Native Media3 repeat modes; the player owns all shuffle/repeat behavior. */
object PlaybackModes {
    fun nextRepeat(current: Int): Int = when (current) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
        else -> Player.REPEAT_MODE_OFF
    }
}
