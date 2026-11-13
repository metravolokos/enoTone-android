package app.enotone.android

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real app shell, no credentials or playback: verifies the Settings route hierarchy
 * without invoking account sign-out, imports, or foreground services.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivitySettingsUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun label(id: Int) = context.getString(id)

    @Test fun settingsGroupsAreOrderedAndDiagnosticsHasBackNavigation() {
        rule.onNodeWithContentDescription(label(R.string.settings_title)).performClick()
        val titles = listOf(
            R.string.settings_group_cloud,
            R.string.settings_group_library,
            R.string.library_pages_settings_title,
            R.string.appearance_title,
            R.string.artwork_settings_title,
            R.string.diagnostics_title,
        )
        // Off-screen coordinates can be clipped: use the rendered semantics order.
        val screenSemantics = rule.onRoot(useUnmergedTree = true).printToString()
        val offsets = titles.map { screenSemantics.indexOf(label(it)) }
        assertTrue("Settings groups must remain in their accepted order",
            offsets.all { it >= 0 } &&
                offsets.zipWithNext().all { (before, after) -> before < after })
        rule.onNodeWithText(label(R.string.diagnostics_title)).performClick()
        rule.onNodeWithText(label(R.string.diagnostics_intro)).assertExists()
        rule.onNodeWithContentDescription(label(R.string.library_back)).performClick()
        rule.onNodeWithText(label(R.string.settings_group_cloud)).assertExists()
        rule.onNodeWithContentDescription(label(R.string.library_back)).performClick()
        rule.onNodeWithText(label(R.string.home_music_title)).assertExists()
    }
}
