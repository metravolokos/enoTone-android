package app.enotone.android.core.ui.component

import android.view.Window
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.test.espresso.Espresso
import app.enotone.android.core.ui.theme.EnoToneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Проверяем поведение диалогового окна, а не наличие определённых строк в Kotlin. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w377dp-h816dp-xxxhdpi")
class EnoActionSheetWindowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun graphiteKeepsStatusBarAndClosesAfterAction() = verifyBarsAndAction(darkTheme = true)

    @Test fun porcelainKeepsStatusBarAndClosesAfterAction() = verifyBarsAndAction(darkTheme = false)

    @Suppress("DEPRECATION")
    private fun verifyBarsAndAction(darkTheme: Boolean) {
        var dialogWindow: Window? = null
        var dismissed = 0
        var invoked = 0
        rule.setContent {
            EnoToneTheme(darkTheme) {
                var visible by remember { mutableStateOf(true) }
                if (visible) {
                    EnoActionSheet(
                        header = EnoActionSheetHeader(
                            title = "Выдуманная песня",
                            subtitle = "Исполнитель",
                            artwork = {
                                val view = LocalView.current
                                SideEffect {
                                    dialogWindow = (view.parent as? DialogWindowProvider)?.window
                                }
                            },
                        ),
                        groups = listOf(listOf(EnoAction(null, "Запустить действие") { invoked++ })),
                        onDismiss = { dismissed++; visible = false },
                    )
                }
            }
        }
        rule.onNodeWithText("Запустить действие").assertExists()
        rule.runOnIdle {
            val window = dialogWindow
            assertNotNull("Лист должен иметь собственное диалоговое окно", window)
            window ?: return@runOnIdle
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            assertEquals(!darkTheme, insetsController.isAppearanceLightStatusBars)
            assertEquals(!darkTheme, insetsController.isAppearanceLightNavigationBars)
            assertEquals(android.graphics.Color.TRANSPARENT, window.statusBarColor)
            assertEquals(false, window.isNavigationBarContrastEnforced)
        }
        rule.onNodeWithText("Запустить действие").performClick()
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(1, dismissed)
            assertEquals(1, invoked)
        }
        rule.onNodeWithText("Запустить действие").assertDoesNotExist()
    }

    @Test fun systemBackDismissesSheetWithoutExecutingAction() {
        var dismissed = 0
        var invoked = 0
        rule.setContent {
            EnoToneTheme(true) {
                var visible by remember { mutableStateOf(true) }
                if (visible) {
                    EnoActionSheet(
                        header = EnoActionSheetHeader("Выдуманная песня", "Исполнитель"),
                        groups = listOf(listOf(EnoAction(null, "Запустить действие") { invoked++ })),
                        onDismiss = { dismissed++; visible = false },
                    )
                }
            }
        }
        rule.onNodeWithText("Запустить действие").assertExists()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, dismissed); assertEquals(0, invoked) }
        rule.onNodeWithText("Запустить действие").assertDoesNotExist()
    }

    @Test fun downwardSwipeDismissesSheet() {
        var dismissed = false
        rule.setContent {
            EnoToneTheme(true) {
                var visible by remember { mutableStateOf(true) }
                if (visible) {
                    EnoActionSheet(
                        header = EnoActionSheetHeader("Выдуманная песня", "Исполнитель"),
                        groups = listOf(listOf(EnoAction(null, "Запустить действие") {})),
                        onDismiss = { dismissed = true; visible = false },
                    )
                }
            }
        }
        rule.onNodeWithText("Запустить действие").assertExists()
        rule.onNodeWithTag(ACTION_SHEET_CONTENT_TAG).performTouchInput {
            swipe(
                start = Offset(center.x, size.height * 0.15f),
                end = Offset(center.x, size.height * 1.75f),
                durationMillis = 480,
            )
        }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(dismissed) }
        rule.onNodeWithText("Запустить действие").assertDoesNotExist()
    }
}
