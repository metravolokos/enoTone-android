package app.enotone.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.media3.common.Player
import androidx.test.espresso.Espresso
import app.enotone.playback.NextBufferState
import app.enotone.playback.PlaybackDataIssue
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.enotone.android.core.ui.theme.EnoToneTheme as CoreEnoToneTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w377dp-h816dp-xhdpi")
class PlaybackErrorSheetUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun errorGroupMappingUsesTheFourApprovedMessages() {
        assertEquals(R.string.error_sheet_no_cloud,
            playbackErrorSheetMessage(PlaybackDataIssue.OPEN_NETWORK, 0))
        assertEquals(R.string.error_sheet_cloud_access,
            playbackErrorSheetMessage(PlaybackDataIssue.OPEN_ACCESS, 0))
        assertEquals(R.string.error_sheet_unreadable,
            playbackErrorSheetMessage(PlaybackDataIssue.OPEN_ENCODING, 0))
        assertEquals(R.string.error_sheet_fallback,
            playbackErrorSheetMessage(PlaybackDataIssue.UNKNOWN, 0))
    }

    @Test fun noErrorDoesNotShowTheSheet() {
        rule.setContent {
            CoreEnoToneTheme(darkTheme = true) {
                PlaybackErrorSheetHost(errorPlayback().copy(playbackState = "PAUSED")) { true }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(NO_CLOUD_MESSAGE).assertDoesNotExist()
    }

    @Test fun dismissStaysDismissedUntilPlaybackLeavesAndReentersError() {
        var playback by mutableStateOf(errorPlayback())
        rule.setContent {
            CoreEnoToneTheme(darkTheme = true) {
                PlaybackErrorSheetHost(playback) { true }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(NO_CLOUD_MESSAGE).assertExists()

        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText(NO_CLOUD_MESSAGE).assertDoesNotExist()
        rule.runOnIdle { playback = playback.copy(title = "A different error detail") }
        rule.waitForIdle()
        rule.onNodeWithText(NO_CLOUD_MESSAGE).assertDoesNotExist()

        rule.runOnIdle { playback = playback.copy(playbackState = "PAUSED") }
        rule.waitForIdle()
        rule.runOnIdle { playback = playback.copy(playbackState = "ERROR") }
        rule.waitForIdle()
        rule.onNodeWithText(NO_CLOUD_MESSAGE).assertExists()
    }

    @Test fun retryClosesOnlyWhenTheConnectionAcceptsTheCommand() {
        var accepted = false
        var retries = 0
        rule.setContent {
            CoreEnoToneTheme(darkTheme = false) {
                PlaybackErrorSheetHost(errorPlayback()) {
                    retries++
                    accepted
                }
            }
        }
        rule.waitForIdle()
        val button = rule.onNodeWithText("Повторить")
        button.performClick()
        rule.waitForIdle()
        rule.onNodeWithText(NO_CLOUD_MESSAGE).assertExists()

        rule.runOnIdle { accepted = true }
        button.performClick()
        rule.waitForIdle()
        rule.onNodeWithText(NO_CLOUD_MESSAGE).assertDoesNotExist()
        rule.runOnIdle { assertEquals(2, retries) }
    }

    @Test fun errorSheetOverNowPlayingGraphite() = screenshot(dark = true, "playback_error_graphite")

    @Test fun errorSheetOverNowPlayingPorcelain() = screenshot(dark = false, "playback_error_porcelain")

    private fun screenshot(dark: Boolean, name: String) {
        rule.setContent {
            CoreEnoToneTheme(darkTheme = dark) {
                var playback by remember { mutableStateOf(errorPlayback()) }
                AppMotionRoot(
                    inSettings = false,
                    diagnostics = false,
                    playerExpanded = true,
                    music = { Box(Modifier.fillMaxSize()) },
                    settings = { _ -> },
                    player = {
                        NowPlayingScreen(
                            trackId = "fixture-track",
                            title = "Песни",
                            artist = null,
                            album = null,
                            artwork = null,
                            playbackState = playback.playbackState,
                            positionMs = 14_000,
                            durationMs = 180_000,
                            canSeek = true,
                            controlsEnabled = true,
                            hasPrevious = true,
                            hasNext = true,
                            queuePosition = 2,
                            queueTotal = 3,
                            repeatMode = Player.REPEAT_MODE_OFF,
                            shuffleEnabled = false,
                            bufferedFraction = 0.2f,
                            hasKnownBufferDuration = true,
                            nextBufferState = NextBufferState.NONE,
                            onClose = {},
                            onSeekFraction = {},
                            onPlayPause = {},
                            onPrevious = {},
                            onNext = {},
                            onCycleRepeat = {},
                            onToggleShuffle = {},
                        )
                    },
                    playbackErrorSheet = { PlaybackErrorSheetHost(playback) { true } },
                )
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun errorPlayback() = PlaybackSnapshot(
        connected = true,
        playbackState = "ERROR",
        currentTrackId = "fixture-track",
        title = "Песни",
        artist = null,
        positionMs = 14_000,
        queuePosition = 2,
        queueTotal = 3,
        errorCode = 2001,
        errorReadIssue = PlaybackDataIssue.OPEN_NETWORK,
    )
}

private const val NO_CLOUD_MESSAGE = "Связи с Облаком нет, самое время немного отдохнуть!"
