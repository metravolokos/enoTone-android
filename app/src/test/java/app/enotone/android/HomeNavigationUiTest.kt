package app.enotone.android

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Домой» C2 (D3): плашка «Перемешать всё» и вход в «Настройки»; разделы медиатеки — в нижней панели. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeNavigationUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun shuffleAllCardShowsCountAndShuffles() {
        var shuffles = 0
        var settings = 0
        rule.setContent {
            EnoToneTheme {
                EnoToneHomeScreen(
                    connectedAccount = true,
                    songCount = 3,
                    onSettings = { settings++ },
                    onShuffleAll = { shuffles++ },
                )
            }
        }
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.home_song_count, 3, 3)).assertExists()
        rule.onNodeWithText(context.getString(R.string.home_shuffle_all)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.settings_title)).performClick()
        rule.runOnIdle {
            assertEquals(1, shuffles)
            assertEquals(1, settings)
        }
    }

    @Test fun emptyOrUnreadLibraryHasNoShuffleCard() {
        rule.setContent {
            EnoToneTheme {
                EnoToneHomeScreen(connectedAccount = true, songCount = null, onSettings = {}, onShuffleAll = {})
            }
        }
        rule.onNodeWithText(context.getString(R.string.home_shuffle_all)).assertDoesNotExist()
    }

    @Test fun disconnectedHomeOpensSettings() {
        var settings = 0
        var shuffles = 0
        rule.setContent {
            EnoToneTheme {
                EnoToneHomeScreen(
                    connectedAccount = false,
                    songCount = null,
                    onSettings = { settings++ },
                    onShuffleAll = { shuffles++ },
                )
            }
        }
        rule.onNodeWithText(context.getString(R.string.home_connect_account)).performClick()
        rule.runOnIdle {
            assertEquals(1, settings)
            assertEquals(0, shuffles)
        }
    }
}
