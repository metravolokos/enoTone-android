package app.enotone.android

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
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
}
