package app.enotone.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** No MediaSession is required: render the real mini-player with fake callbacks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MiniPlayerUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun visibleOnlyWithQueueAndOpensOnTrackTap() {
        var queueSize by mutableIntStateOf(0)
        var opens = 0
        rule.setContent {
            EnoToneTheme {
                EnoToneMiniPlayer(
                    player = SharedPlayerUiState(
                        playback = PlaybackSnapshot(
                            connected = true, playbackState = "PAUSED",
                            title = "Fixture mini track", artist = "Fixture artist",
                            queueTotal = queueSize, hasNext = true,
                        ),
                    ),
                    onOpen = { opens++ }, onPlayPause = {}, onNext = {},
                )
            }
        }
        rule.onNodeWithText("Fixture mini track").assertDoesNotExist()
        rule.runOnIdle { queueSize = 2 }
        rule.onNodeWithText("Fixture mini track").assertExists()
        rule.onNodeWithText("Fixture mini track").performClick()
        rule.runOnIdle { assertEquals(1, opens) }
        rule.runOnIdle { queueSize = 0 }
        rule.onNodeWithText("Fixture mini track").assertDoesNotExist()
    }
}
