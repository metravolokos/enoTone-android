package app.enotone.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackModesTest {
    @Test fun cyclesOffOneAllThenOff() {
        var mode = Player.REPEAT_MODE_OFF
        mode = PlaybackModes.nextRepeat(mode)
        assertEquals(Player.REPEAT_MODE_ONE, mode)
        mode = PlaybackModes.nextRepeat(mode)
        assertEquals(Player.REPEAT_MODE_ALL, mode)
        mode = PlaybackModes.nextRepeat(mode)
        assertEquals(Player.REPEAT_MODE_OFF, mode)
    }

    @Test fun unknownModeSafelyResetsToOff() {
        assertEquals(Player.REPEAT_MODE_OFF, PlaybackModes.nextRepeat(-1))
        assertEquals(Player.REPEAT_MODE_OFF, PlaybackModes.nextRepeat(123456))
    }
}
