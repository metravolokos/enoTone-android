package app.enotone.android

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Настоящий NowPlayingScreen с синтетическими транспортными данными, без Media3/сети. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class AppNowPlayingGestureUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun slowShortSwipeReturnsToOriginAndLongSwipeClosesWithoutHostRecomposition() {
        var closeCount = 0
        var hostCompositions = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SideEffect { hostCompositions++ }
            EnoToneTheme {
                NowPlayingScreen(
                    trackId = "gesture-id", title = "Gesture song", artist = "Artist",
                    album = "Album", artwork = null, playbackState = "PAUSED",
                    errorCode = 0, positionMs = 4000L, durationMs = 120000L,
                    canSeek = true, controlsEnabled = true, hasPrevious = true, hasNext = true,
                    queuePosition = 1, queueTotal = 2,
                    repeatMode = Player.REPEAT_MODE_OFF, shuffleEnabled = false,
                    onClose = { closeCount++ }, onSeekFraction = {}, onPlayPause = {},
                    onPrevious = {}, onNext = {}, onCycleRepeat = {}, onToggleShuffle = {},
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        val origin = top()
        val compositions = hostCompositions
        rule.onNodeWithTag("now-playing-gesture-root", useUnmergedTree = true)
            .performTouchInput {
                swipe(Offset(center.x, top + 70f), Offset(center.x, top + 115f),
                    durationMillis = 600)
            }
        rule.mainClock.advanceTimeBy(300)
        rule.runOnIdle {
            assertEquals("Short swipe must not close", 0, closeCount)
            assertEquals("Dragging must not recompose parent player host", compositions, hostCompositions)
            assertTrue("Cancelled swipe must restore root",
                kotlin.math.abs(top() - origin) <= 4f)
        }
        rule.onNodeWithTag("now-playing-gesture-root", useUnmergedTree = true)
            .performTouchInput {
                swipe(Offset(center.x, top + 50f), Offset(center.x, bottom - 80f),
                    durationMillis = 600)
            }
        rule.runOnIdle { assertEquals("Over-threshold swipe must close", 1, closeCount) }
    }

    private fun top(): Float =
        rule.onAllNodesWithTag("now-playing-gesture-root", useUnmergedTree = true)
            .fetchSemanticsNodes().single().boundsInRoot.top
}
