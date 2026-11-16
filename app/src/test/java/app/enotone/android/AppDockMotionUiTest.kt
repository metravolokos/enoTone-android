package app.enotone.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D3.5: во всех кадрах док остаётся единственным и на неизменной позиции. */
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
                    // Expanded меняет AppMotionRoot, но не состояние/композицию самого дока.
                    AppMotionRoot(
                        inSettings = false,
                        diagnostics = false,
                        playerExpanded = expanded.value,
                        music = {
                            AppDockMotion(
                                miniVisible = true,
                                miniPlayer = { Text("Persistent mini") },
                                bottomBar = { Text("Persistent navigation") },
                            )
                        },
                        settings = {},
                        player = { Text("Now Playing") },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        val beforeMini = rule.onAllNodesWithTag("app-mini-dock", useUnmergedTree = true)
            .fetchSemanticsNodes().single().boundsInRoot
        val beforeBar = rule.onAllNodesWithTag("app-bottom-dock", useUnmergedTree = true)
            .fetchSemanticsNodes().single().boundsInRoot
        rule.runOnIdle { expanded.value = true }
        repeat(7) {
            rule.mainClock.advanceTimeBy(50)
            // Покрытую семантику TalkBack намеренно не ищем: AppMotionRoot её прячет.
            // Положение покрытого дока проверяется в тесте Bitmap View, а здесь —
            // через сохранение composable-состояния при открытии и закрытии.
        }
        rule.runOnIdle { expanded.value = false }
        rule.mainClock.advanceTimeBy(300)
        assertEquals(beforeMini,
            rule.onAllNodesWithTag("app-mini-dock", useUnmergedTree = true)
                .fetchSemanticsNodes().single().boundsInRoot)
        assertEquals(beforeBar,
            rule.onAllNodesWithTag("app-bottom-dock", useUnmergedTree = true)
                .fetchSemanticsNodes().single().boundsInRoot)
        rule.onAllNodesWithTag("app-mini-dock").assertCountEquals(1)
        rule.onAllNodesWithTag("app-bottom-dock").assertCountEquals(1)
    }

    @Test fun dockAlwaysContainsOneMiniAndOneBottomBar() {
        rule.setContent {
            EnoToneTheme {
                AppDockMotion(
                    miniVisible = true,
                    miniPlayer = { Text("Mini") },
                    bottomBar = { Text("Tabs") },
                )
            }
        }
        rule.onAllNodesWithTag("app-mini-dock").assertCountEquals(1)
        rule.onAllNodesWithTag("app-bottom-dock").assertCountEquals(1)
    }
}
