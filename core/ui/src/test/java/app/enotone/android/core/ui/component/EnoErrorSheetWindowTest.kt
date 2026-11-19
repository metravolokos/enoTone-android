package app.enotone.android.core.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import app.enotone.android.core.ui.theme.EnoToneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Проверяет высоту листа, видимость кнопки и три способа закрытия ModalBottomSheet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w377dp-h816dp-xxxhdpi", fontScale = 2f)
class EnoErrorSheetWindowTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun longMessageStaysWithinThreeQuartersAndKeepsActionVisibleAtFontScale2() {
        setErrorSheet()

        rule.onAllNodesWithTag(ENO_ERROR_SHEET_CONTENT_TAG).assertCountEquals(1)
        rule.onNodeWithTag(ENO_ERROR_SHEET_CONTENT_TAG).assertExists()
        rule.onNodeWithTag(ENO_ERROR_SHEET_ACTION_TAG).assertIsDisplayed()
        val heightPx = rule.onNodeWithTag(ENO_ERROR_SHEET_CONTENT_TAG)
            .fetchSemanticsNode().boundsInRoot.height
        val maxHeightPx = with(rule.density) { 816.dp.toPx() * 0.75f }
        assertTrue("sheet height $heightPx px exceeds 75% screen height $maxHeightPx px", heightPx <= maxHeightPx)

        val scrollContainer = rule.onNodeWithTag(ENO_ERROR_SHEET_MESSAGE_TAG)
        val scrollNode = scrollContainer.fetchSemanticsNode()
        val beforeScrollRange = scrollNode.config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(
            "long message needs a scroll range",
            beforeScrollRange.maxValue() > 0f,
        )
    }

    @Test
    fun backDismissesWithoutRunningTheAction() {
        setErrorSheet()
        rule.onNodeWithText("Повторить").assertIsDisplayed()

        Espresso.pressBack()
        rule.waitForIdle()

        rule.runOnIdle {
            assertEquals(1, dismissCount)
            assertEquals(0, actionCount)
        }
        rule.onNodeWithTag(ENO_ERROR_SHEET_CONTENT_TAG).assertDoesNotExist()
    }

    @Test
    fun downwardSwipeDismissesSheet() {
        setErrorSheet()
        rule.onNodeWithText("Повторить").assertExists()

        rule.onNodeWithTag(ENO_ERROR_SHEET_CONTENT_TAG).performTouchInput {
            swipe(
                start = Offset(center.x, 20f),
                end = Offset(center.x, 700f),
                durationMillis = 480,
            )
        }
        rule.waitForIdle()

        rule.runOnIdle { assertEquals(1, dismissCount) }
        rule.onNodeWithTag(ENO_ERROR_SHEET_CONTENT_TAG).assertDoesNotExist()
    }

    @Test
    fun tappingOutsideDismissesSheet() {
        setErrorSheet()
        rule.onNodeWithText("Повторить").assertExists()

        val roots = rule.onAllNodes(isRoot()).fetchSemanticsNodes()
        val dialogRootIndex = roots.indexOfFirst {
            it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f
        }
        assertTrue("ModalBottomSheet dialog root should be present", dialogRootIndex >= 0)
        rule.onAllNodes(isRoot()).get(dialogRootIndex)
            .performTouchInput { click(Offset(10f, 10f)) }
        rule.waitForIdle()

        rule.runOnIdle { assertEquals(1, dismissCount) }
        rule.onNodeWithTag(ENO_ERROR_SHEET_CONTENT_TAG).assertDoesNotExist()
    }

    private var dismissCount = 0
    private var actionCount = 0

    private fun setErrorSheet() {
        dismissCount = 0
        actionCount = 0
        rule.setContent {
            EnoToneTheme(darkTheme = true) {
                var visible by remember { mutableStateOf(true) }
                if (visible) {
                    EnoErrorSheet(
                        message = LONG_ERROR_MESSAGE,
                        actionLabel = "Повторить",
                        onAction = { actionCount++ },
                        onDismiss = { dismissCount++; visible = false },
                    )
                }
            }
        }
    }
}

private const val LONG_ERROR_MESSAGE =
    "Пока я не могу обновить медиатеку: облако слишком долго не отвечает.\n" +
        "Проверьте подключение к интернету и попробуйте ещё раз.\n" +
        "Уже загруженные песни останутся доступны.\n" +
        "Новое подключение можно проверить позже.\n" +
        "Если соединение восстановится, продолжу с того же места.\n" +
        "Я сохраню найденную музыку и продолжу синхронизацию.\n" +
        "Пока облако не отвечает, можно закрыть это сообщение.\n" +
        "Когда будете готовы, попробуйте ещё раз.\n" +
        "Сохранённые данные останутся доступны и при следующей попытке."
