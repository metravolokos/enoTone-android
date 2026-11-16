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
