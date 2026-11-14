package app.enotone.android

import android.content.Context
import app.enotone.playback.PlaybackModes
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
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
        // D3.2 (владелец): кнопки/ручки «Свернуть» нет — экран закрывается только жестом вниз.
        rule.onNodeWithContentDescription(context.getString(R.string.library_player_close))
            .assertDoesNotExist()
        rule.onRoot().performTouchInput { swipeDown(startY = top + 40f, endY = bottom - 40f) }
        rule.runOnIdle { assertEquals(1, close) }
        rule.onNodeWithContentDescription("Громкость").assertDoesNotExist()
        rule.onNodeWithContentDescription("Volume").assertDoesNotExist()
        // No invented quality labels or non-functional controls.
        for (label in listOf("Lyrics", "Cast", "Favorites", "Queue", "Lossless",
            "Текст песни", "Избранное", "Очередь", "Без потерь")) {
            rule.onNodeWithContentDescription(label).assertDoesNotExist()
            rule.onNodeWithText(label).assertDoesNotExist()
        }
    }

    @Test fun repeatCycleAndShuffleRespondToRealTouchesAndRecompose() {
        val repeat = mutableIntStateOf(Player.REPEAT_MODE_OFF)
        val shuffle = mutableStateOf(false)
        rule.setContent {
            EnoToneTheme {
                NowPlayingScreen(
                    trackId = "fixture-id", title = "Fixture", artist = null,
                    album = null, artwork = null, playbackState = "PAUSED",
                    errorCode = 0, positionMs = 0, durationMs = 60_000,
                    canSeek = true, controlsEnabled = true,
                    hasPrevious = true, hasNext = true,
                    queuePosition = 1, queueTotal = 3,
                    repeatMode = repeat.intValue, shuffleEnabled = shuffle.value,
                    onClose = {}, onSeekFraction = {}, onPlayPause = {},
                    onPrevious = {}, onNext = {},
                    onCycleRepeat = {
                        repeat.intValue = PlaybackModes.nextRepeat(repeat.intValue)
                    },
                    onToggleShuffle = { shuffle.value = !shuffle.value },
                )
            }
        }

        fun repeatLabel(mode: Int): String {
            val labelId = when (mode) {
                Player.REPEAT_MODE_ONE -> R.string.playback_repeat_one
                Player.REPEAT_MODE_ALL -> R.string.playback_repeat_all
                else -> R.string.playback_repeat_off
            }
            return context.getString(R.string.playback_repeat_control,
                context.getString(labelId))
        }

        for ((from, to) in listOf(
            Player.REPEAT_MODE_OFF to Player.REPEAT_MODE_ONE,
            Player.REPEAT_MODE_ONE to Player.REPEAT_MODE_ALL,
            Player.REPEAT_MODE_ALL to Player.REPEAT_MODE_OFF,
        )) {
            rule.onNodeWithContentDescription(repeatLabel(from))
                .performTouchInput { click() }
            rule.onNodeWithContentDescription(repeatLabel(to)).assertExists()
            rule.runOnIdle { assertEquals(to, repeat.intValue) }
        }
        rule.onNodeWithContentDescription(context.getString(R.string.playback_shuffle_off))
            .performTouchInput { click() }
        rule.onNodeWithContentDescription(context.getString(R.string.playback_shuffle_on))
            .assertExists()
        rule.runOnIdle { assertEquals(true, shuffle.value) }
        rule.onNodeWithContentDescription(context.getString(R.string.playback_shuffle_on))
            .performTouchInput { click() }
        rule.onNodeWithContentDescription(context.getString(R.string.playback_shuffle_off))
            .assertExists()
        rule.runOnIdle { assertEquals(false, shuffle.value) }
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
