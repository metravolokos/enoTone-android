package app.enotone.android

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.enotone.library.LibraryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A fake mini-player slot verifies that Home offers the actual music destinations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeNavigationUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun homeOpensAlbumsAndHidesNonSelectedPages() {
        var selected: LibraryPage? = null
        rule.setContent {
            EnoToneTheme {
                EnoToneHomeScreen(
                    visiblePages = listOf(LibraryPage.TRACKS, LibraryPage.ALBUMS),
                    connectedAccount = true, onSettings = {},
                    onOpenPage = { selected = it },
                    miniPlayer = { Text("MINI_PLAYER_SENTINEL") },
                )
            }
        }
        rule.onNodeWithText("MINI_PLAYER_SENTINEL").assertExists()
        rule.onAllNodesWithText(context.getString(R.string.library_tab_artists)).assertCountEquals(0)
        rule.onAllNodesWithText(context.getString(R.string.library_tab_albums))[0].performClick()
        rule.runOnIdle { assertEquals(LibraryPage.ALBUMS, selected) }
    }

    @Test fun disconnectedHomeOpensSettingsNotOfflineLibrary() {
        var settings = 0
        var selected: LibraryPage? = null
        rule.setContent {
            EnoToneTheme {
                EnoToneHomeScreen(
                    visiblePages = listOf(LibraryPage.TRACKS),
                    connectedAccount = false,
                    onSettings = { settings++ },
                    onOpenPage = { selected = it },
                )
            }
        }
        rule.onNodeWithText(context.getString(R.string.home_connect_account)).performClick()
        rule.runOnIdle {
            assertEquals(1, settings)
            assertNull(selected)
        }
    }
}
