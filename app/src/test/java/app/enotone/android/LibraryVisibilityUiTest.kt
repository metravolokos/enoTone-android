package app.enotone.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.performClick
import app.enotone.library.LibraryPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Composable checkbox interactions delegate to the actual page-visibility policy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryVisibilityUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun hiddenPageIsReportedAndLastVisiblePageCannotBeDisabled() {
        var hidden by mutableStateOf(emptySet<LibraryPage>())
        rule.setContent {
            EnoToneTheme {
                LibraryVisibilitySettingsContent(hiddenPages = hidden) { hidden = it }
            }
        }
        rule.onAllNodes(isToggleable()).assertCountEquals(4)
        rule.onAllNodes(isToggleable())[2].performClick()
        rule.runOnIdle { assertEquals(setOf(LibraryPage.ARTISTS), hidden) }
        rule.onAllNodes(isToggleable())[2].assertIsOff()
        rule.onAllNodes(isToggleable())[0].performClick()
        rule.onAllNodes(isToggleable())[1].performClick()
        rule.onAllNodes(isToggleable())[3].assertIsOn()
        // The sole remaining destination must not be removable.
        rule.onAllNodes(isToggleable())[3].assertIsNotEnabled()
        rule.runOnIdle {
            assertEquals(setOf(LibraryPage.TRACKS, LibraryPage.ALBUMS, LibraryPage.ARTISTS), hidden)
        }
    }
}
