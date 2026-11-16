package app.enotone.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import app.enotone.library.LibraryPage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D3.5, красные тесты на main до удаления D3.4.
 * Проверяют промежуточные кадры реального Compose, а не наличие строк в исходниках.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class AppSimpleMotionUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun predictiveBackKeepsDetailAtFullScale() {
        val detail = AppNavigator.push(
            AppNavState(tab = AppTab.Library(LibraryPage.ARTISTS)),
            LibraryDetail.Artist("artist"),
        )
        rule.setContent {
            EnoToneTheme {
                AppRouteBackLayer(
                    route = detail,
                    playerExpanded = false,
                    previewRoute = appRouteKey(detail),
                    progress = 0.5f,
                ) { Text("Current detail") }
            }
        }
        rule.onAllNodes(SemanticsMatcher.expectValue(AppBackScaleKey, 1f)).assertCountEquals(1)
    }

    @Test fun settingsEnterWithoutHorizontalOffset() {
        val settingsOpen = mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            EnoToneTheme {
                AppMotionRoot(
                    inSettings = settingsOpen.value,
                    diagnostics = false,
                    playerExpanded = false,
                    music = { Box(Modifier.fillMaxSize().testTag("motion-library")) },
                    settings = { Box(Modifier.fillMaxSize().testTag("motion-settings")) },
                    player = {},
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { settingsOpen.value = true }
        rule.mainClock.advanceTimeBy(48)
        val at48ms = rule.onNodeWithTag("motion-settings", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left
        rule.mainClock.advanceTimeBy(300)
        val settled = rule.onNodeWithTag("motion-settings", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left
        org.junit.Assert.assertEquals(
            "Настройки не должны смещаться по горизонтали в промежуточном кадре",
            settled, at48ms, 1f,
        )
    }

    @Test fun playerOpensAsOneVerticalSheetWithoutRecreatingUnderlyingRoute() {
        val expanded = mutableStateOf(false)
        var musicMounts = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            EnoToneTheme {
                AppMotionRoot(
                    inSettings = false,
                    diagnostics = false,
                    playerExpanded = expanded.value,
                    music = {
                        androidx.compose.runtime.DisposableEffect(Unit) {
                            musicMounts++
                            onDispose { musicMounts-- }
                        }
                        Box(Modifier.fillMaxSize().background(Color.Red).testTag("music"))
                    },
                    settings = {},
                    player = { Box(Modifier.fillMaxSize().background(Color.Blue).testTag("sheet")) },
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { expanded.value = true }
        repeat(6) {
            rule.mainClock.advanceTimeBy(48)
            rule.runOnIdle { assertTrue("Музыка не должна пересоздаваться", musicMounts == 1) }
            val count = rule.onAllNodesWithTag("sheet", useUnmergedTree = true)
                .fetchSemanticsNodes().size
            assertTrue("Шторка существует не более одной копии", count <= 1)
        }
    }
}
