package app.enotone.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D3.5: шторка не создаёт вторую копию мини-плеера при промежуточном жесте. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDockMotionUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dockRemainsVisibleAndStationaryWhilePlayerIsExpanded() {
        val expanded = mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            EnoToneTheme {
                Box(Modifier.fillMaxSize()) {
                    AppDockMotion(
                        playerExpanded = expanded.value,
                        miniVisible = true,
                        miniPlayer = { Text("Persistent mini") },
                        bottomBar = { Text("Persistent navigation") },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(300)
        val beforeMini = rule.onAllNodesWithTag("app-mini-dock").fetchSemanticsNodes().single().boundsInRoot
        val beforeBar = rule.onAllNodesWithTag("app-bottom-dock").fetchSemanticsNodes().single().boundsInRoot
        rule.runOnIdle { expanded.value = true }
        repeat(7) {
            rule.mainClock.advanceTimeBy(50)
            val mini = rule.onAllNodesWithTag("app-mini-dock").fetchSemanticsNodes()
            val bar = rule.onAllNodesWithTag("app-bottom-dock").fetchSemanticsNodes()
            org.junit.Assert.assertEquals("Mini must remain composed during sheet", 1, mini.size)
            org.junit.Assert.assertEquals("Navigation must remain composed during sheet", 1, bar.size)
            org.junit.Assert.assertEquals("Mini must not move", beforeMini, mini.single().boundsInRoot)
            org.junit.Assert.assertEquals("Navigation must not move", beforeBar, bar.single().boundsInRoot)
        }
    }

    @Test fun thereIsNeverMoreThanOneMiniPlayerWhenSheetAppears() {
        val expanded = mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            EnoToneTheme {
                Box(Modifier.fillMaxSize()) {
                    AppDockMotion(
                        playerExpanded = expanded.value,
                        miniVisible = true,
                        miniPlayer = { Text("Mini") },
                        bottomBar = { Text("Tabs") },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onAllNodesWithTag("app-mini-dock").assertCountEquals(1)
        rule.runOnIdle { expanded.value = true }
        repeat(6) {
            rule.mainClock.advanceTimeBy(48)
            val count = rule.onAllNodesWithTag("app-mini-dock").fetchSemanticsNodes().size
            org.junit.Assert.assertTrue("Не может быть двух мини-плееров: $count", count <= 1)
        }
        rule.mainClock.advanceTimeBy(300)
        rule.onAllNodesWithTag("app-mini-dock").assertCountEquals(0)
    }
}
