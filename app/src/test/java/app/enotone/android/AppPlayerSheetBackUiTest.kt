package app.enotone.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Реальный AppMotionRoot: обратный системный жест использует одну вертикальную траекторию. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class AppPlayerSheetBackUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun previewCancelAndCommitKeepContinuousSheetOffset() {
        val expanded = mutableStateOf(true)
        val progress = mutableFloatStateOf(0f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            EnoToneTheme {
                AppMotionRoot(
                    inSettings = false,
                    diagnostics = false,
                    playerExpanded = expanded.value,
                    playerBackProgress = progress.floatValue,
                    music = { Box(Modifier.fillMaxSize().background(Color.Red)) },
                    settings = {},
                    player = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
                )
            }
        }
        rule.mainClock.advanceTimeBy(400)
        val rest = top()
        rule.runOnIdle { progress.floatValue = 0.5f }
        rule.mainClock.advanceTimeByFrame()
        val half = top()
        assertTrue("Preview must move sheet downward: rest=$rest half=$half", half > rest + 100f)

        rule.runOnIdle { progress.floatValue = 0f }
        rule.mainClock.advanceTimeBy(100)
        val returning = top()
        assertTrue("Cancel must animate back rather than jump", returning > rest && returning < half)
        rule.mainClock.advanceTimeBy(250)
        val restored = top()
        assertTrue("Cancel must restore origin: $rest vs $restored",
            kotlin.math.abs(restored - rest) <= 4f)

        rule.runOnIdle { progress.floatValue = 0.5f }
        rule.mainClock.advanceTimeByFrame()
        val beforeCommit = top()
        rule.runOnIdle { expanded.value = false }
        rule.mainClock.advanceTimeByFrame()
        val firstExit = top()
        assertTrue("Commit must not jump: $beforeCommit → $firstExit",
            firstExit >= beforeCommit - 4f && firstExit < beforeCommit + 70f)
        rule.mainClock.advanceTimeBy(110)
        val laterExit = top()
        assertTrue("Exit must continue downward: $firstExit → $laterExit", laterExit > firstExit)
        rule.mainClock.advanceTimeBy(240)
        rule.onAllNodesWithTag("app-player-sheet", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    private fun top(): Float = rule.onAllNodesWithTag(
        "app-player-sheet", useUnmergedTree = true,
    ).fetchSemanticsNodes().single().boundsInRoot.top
}
