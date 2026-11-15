package app.enotone.android

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.enotone.android.core.ui.component.EnoBottomBar
import app.enotone.android.core.ui.component.EnoBottomBarItem
import app.enotone.android.core.ui.component.EnoIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Действительный dock-контейнер, EnoBottomBar и EnoToneMiniPlayer, не Text-заглушки. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class AppDockMotionUiTest {
    @get:Rule val rule = createComposeRule()

    private data class Position(val bar: Float, val mini: Float)

    private fun position(): Position {
        rule.waitForIdle()
        return Position(
            rule.onNodeWithTag("app-bottom-dock").fetchSemanticsNode().config[AppDockOffsetKey],
            rule.onNodeWithTag("app-mini-dock").fetchSemanticsNode().config[AppDockOffsetKey],
        )
    }

    private fun updateAndRender(action: () -> Unit) {
        rule.runOnIdle(action)
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    private class HostState {
        var expanded by mutableStateOf(true)
        var drag by mutableFloatStateOf(0f)
        val nav = AppNavState()
        val controller = AppBackController()
    }

    private fun render(): HostState {
        val host = HostState()
        val player = SharedPlayerUiState(
            playback = PlaybackSnapshot(
                connected = true, queueTotal = 1, title = "Song", artist = "Performer",
            ),
        )
        rule.mainClock.autoAdvance = false
        rule.setContent {
            EnoToneTheme {
                Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppDockMotion(
                            playerExpanded = host.expanded,
                            miniVisible = true,
                            dragProgress = host.drag,
                            backProgress = host.controller.playerProgress(),
                            miniPlayer = {
                                EnoToneMiniPlayer(
                                    player = player, onOpen = {}, onPlayPause = {}, onNext = {},
                                )
                            },
                            bottomBar = {
                                EnoBottomBar(
                                    items = listOf(EnoBottomBarItem(EnoIcons.HOME, "Home")),
                                    selectedIndex = 0, onSelect = {},
                                )
                            },
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        return host
    }

    @Test fun dragMovesRealDockAtQuarterAndHalfAndReturnsOnCancelOrCommit() {
        val host = render()
        val hidden = position()
        updateAndRender { host.drag = 0.25f }
        rule.mainClock.advanceTimeBy(48)
        val quarter = position()
        updateAndRender { host.drag = 0.5f }
        rule.mainClock.advanceTimeBy(48)
        val half = position()
        println("DOCK drag hidden=$hidden quarter=$quarter half=$half")
        // Bottom bar enters from below, mini player from above. Both follow gesture progression.
        assertTrue("Bottom bar at 25%: $hidden -> $quarter", hidden.bar > quarter.bar)
        assertTrue("Bottom bar at 50%: $quarter -> $half", quarter.bar > half.bar)
        assertTrue("Mini at 25%: $hidden -> $quarter", hidden.mini < quarter.mini)
        assertTrue("Mini at 50%: $quarter -> $half", quarter.mini < half.mini)

        updateAndRender { host.drag = 0f }
        rule.mainClock.advanceTimeBy(AppMotionSpec.PLAYER_CLOSE_MS.toLong() + 50)
        val cancelled = position()
        assertEquals("Cancel restores the hidden bottom dock", hidden.bar, cancelled.bar, 2f)
        assertEquals("Cancel restores the hidden mini", hidden.mini, cancelled.mini, 2f)

        updateAndRender { host.drag = 0.5f }
        rule.mainClock.advanceTimeBy(48)
        updateAndRender { host.expanded = false }
        rule.mainClock.advanceTimeBy(AppMotionSpec.PLAYER_CLOSE_MS.toLong() + 50)
        val shown = position()
        assertTrue("The bottom bar must travel between hidden and fully shown",
            half.bar > shown.bar)
        assertTrue("The mini must travel between hidden and fully shown",
            half.mini < shown.mini)
    }

    @Test fun predictivePlayerBackDrivesDockAndCancelReturnsIt() {
        val host = render()
        val hidden = position()
        updateAndRender { host.controller.preview(AppBackTarget.PLAYER, host.nav, 0.5f) }
        rule.mainClock.advanceTimeBy(48)
        val half = position()
        println("DOCK predictive hidden=$hidden half=$half")
        assertTrue("Predictive dock: $hidden -> $half",
            hidden.bar > half.bar && hidden.mini < half.mini)
        updateAndRender { host.controller.cancel() }
        rule.mainClock.advanceTimeBy(AppMotionSpec.PLAYER_CLOSE_MS.toLong() + 50)
        val cancelled = position()
        assertEquals(hidden.bar, cancelled.bar, 2f)
        assertEquals(hidden.mini, cancelled.mini, 2f)
    }

    @Test fun systemAnimationScaleZeroKeepsDockAtFinalState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val original = Settings.Global.getFloat(context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            val host = render()
            val hidden = position()
            updateAndRender { host.controller.preview(AppBackTarget.PLAYER, host.nav, 0.5f) }
            rule.mainClock.advanceTimeByFrame()
            val mid = position()
            updateAndRender { host.expanded = false }
            rule.mainClock.advanceTimeBy(AppMotionSpec.PLAYER_CLOSE_MS.toLong() + 50)
            val shown = position()
            println("DOCK scale0 hidden=$hidden mid=$mid shown=$shown")
            assertTrue(mid.bar < hidden.bar && shown.bar < hidden.bar)
        } finally {
            Settings.Global.putFloat(context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, original)
        }
    }
}
