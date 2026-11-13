package app.enotone.android

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Uses transport callbacks, not Media3/network, to verify the rendered controls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class NowPlayingUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun transportButtonsCallTheirActionsAndVolumeIsAbsent() {
        var close = 0
        var play = 0
        var previous = 0
        var next = 0
        var shuffle = 0
        var repeat = 0
        rule.setContent {
            EnoToneTheme {
                NowPlayingScreen(
                    trackId = "fixture-id", title = "Fixture title", artist = "Fixture artist",
                    album = "Fixture album", artwork = null, playbackState = "PAUSED",
                    errorCode = 0, positionMs = 15000, durationMs = 120000,
                    canSeek = true, controlsEnabled = true,
                    hasPrevious = true, hasNext = true,
                    queuePosition = 2, queueTotal = 3,
                    repeatMode = Player.REPEAT_MODE_OFF, shuffleEnabled = false,
                    onClose = { close++ }, onSeekFraction = {}, onPlayPause = { play++ },
                    onPrevious = { previous++ }, onNext = { next++ },
                    onCycleRepeat = { repeat++ }, onToggleShuffle = { shuffle++ },
                )
            }
        }
        rule.onNodeWithText("Fixture title").assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.playback_resume)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.playback_previous)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.playback_next)).performClick()
        rule.onNode(hasContentDescription(context.getString(R.string.playback_shuffle_off))
            and hasClickAction()).performClick()
        rule.runOnIdle {
            assertEquals(0, close)
            assertEquals(1, play)
            assertEquals(1, previous)
            assertEquals(1, next)
            assertEquals(1, shuffle)
        }
        rule.onNodeWithContentDescription(
            context.getString(R.string.playback_repeat_control,
                context.getString(R.string.playback_repeat_off)),
        ).performClick()
        rule.runOnIdle { assertEquals(1, repeat) }
        rule.onNodeWithContentDescription(context.getString(R.string.library_player_close))
            .performClick()
        rule.runOnIdle { assertEquals(1, close) }
        rule.onNodeWithContentDescription("Громкость").assertDoesNotExist()
        rule.onNodeWithContentDescription("Volume").assertDoesNotExist()
    }

    @Test fun disabledTransportIsNotClickableAndKeepsTimeVisible() {
        var play = 0
        rule.setContent {
            EnoToneTheme {
                NowPlayingScreen(
                    trackId = "fixture-id", title = "Paused title", artist = null,
                    album = null, artwork = null, playbackState = "PAUSED", errorCode = 0,
                    positionMs = 30000, durationMs = 120000,
                    canSeek = false, controlsEnabled = false,
                    hasPrevious = false, hasNext = false,
                    queuePosition = 1, queueTotal = 1,
                    repeatMode = Player.REPEAT_MODE_OFF, shuffleEnabled = false,
                    onClose = {}, onSeekFraction = {}, onPlayPause = { play++ },
                    onPrevious = {}, onNext = {}, onCycleRepeat = {}, onToggleShuffle = {},
                )
            }
        }
        rule.onNodeWithText("00:30").assertExists()
        rule.onNodeWithText("-01:30").assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.playback_resume)).assertExists()
        rule.runOnIdle { assertEquals(0, play) }
    }
}
