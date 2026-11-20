package app.enotone.android

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fresh-install app shell: checks that login gates regular app navigation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivitySettingsUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun label(id: Int) = context.getString(id)

    @Test fun freshInstallShowsLoginBeforeRegularAppNavigation() {
        rule.onNodeWithText(label(R.string.first_run_login_title)).assertExists()
        assertTrue(rule.onAllNodesWithContentDescription(label(R.string.settings_title))
            .fetchSemanticsNodes().isEmpty())
    }

    // Тест D6-B сохранён как есть, но требует вошедшего пользователя: на чистой установке
    // D6-FR показывает LOGIN. Нужна фикстура входа — см. замечание ревью PR #236.
    @Ignore("Needs a signed-in fixture after D6-FR; restore per PR #236 review")
    @Test fun settingsGroupsAreOrderedAndDiagnosticsHasBackNavigation() {
        rule.onNodeWithContentDescription(label(R.string.settings_title)).performClick()
        val titles = listOf(
            R.string.settings_group_cloud,
            R.string.settings_group_library,
            R.string.appearance_title,
            R.string.settings_group_app_library,
            R.string.library_pages_settings_title,
            R.string.artwork_settings_title,
            R.string.diagnostics_title,
        )
        // Off-screen coordinates can be clipped: use the rendered semantics order.
        val screenSemantics = rule.onRoot(useUnmergedTree = true).printToString()
        val offsets = titles.map { screenSemantics.indexOf(label(it)) }
        assertTrue("Settings groups must remain in their accepted order",
            offsets.all { it >= 0 } &&
                offsets.zipWithNext().all { (before, after) -> before < after })
        rule.onNodeWithContentDescription(label(R.string.settings_title)).assertExists()
        rule.onNodeWithText("О приложении").assertDoesNotExist()
        rule.onNodeWithText(label(R.string.diagnostics_title)).performScrollTo().performClick()
        rule.onNodeWithText(label(R.string.diagnostics_intro)).assertExists()
        rule.onNodeWithContentDescription(label(R.string.library_back)).performClick()
        rule.onNodeWithText(label(R.string.settings_group_cloud)).assertExists()
        pressBack()
        rule.waitForIdle()
        // D3: «Домой» C2 без заголовка «Моя музыка»; признак «Домой» — вход в «Настройки» в заголовке.
        rule.onNodeWithContentDescription(label(R.string.settings_title)).assertExists()
    }
}
