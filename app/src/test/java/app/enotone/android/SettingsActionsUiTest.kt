package app.enotone.android

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Explicitly refuses to invoke destructive reindex/sign-out in UI tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsActionsUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun fullReindexConfirmationCanBeCancelledWithoutStarting() {
        var showing by mutableStateOf(false)
        var confirmations = 0
        val actions = object : IndexingActions {
            override fun showReindex(value: Boolean) { showing = value }
            override fun confirmReindex() { confirmations++ }
        }
        rule.setContent {
            EnoToneTheme {
                Column {
                    SettingsIndexingSection(
                        LibraryIndexingUiState(hasLogin = true, confirmReindex = showing),
                        actions,
                    )
                }
            }
        }
        rule.onNodeWithText(context.getString(R.string.library_reindex_action)).performClick()
        rule.onNodeWithText(context.getString(R.string.library_reindex_confirm_title)).assertExists()
        rule.onNodeWithText(context.getString(R.string.album_refresh_cancel)).performClick()
        rule.onNodeWithText(context.getString(R.string.library_reindex_confirm_title)).assertDoesNotExist()
        rule.runOnIdle {
            assertFalse(showing)
            assertEquals(0, confirmations)
        }
    }

    @Test fun signOutDialogCancelNeverCallsDestructiveAction() {
        var cancels = 0
        var signOuts = 0
        rule.setContent {
            EnoToneTheme {
                AccountSettingsSection(
                    account = AccountUiState(
                        loggedIn = true, email = "fixture@example.invalid",
                        restoring = false, showConfirmation = true, trackCount = 35,
                    ),
                    onEmailChange = {}, onPasswordChange = {}, onVerify = {},
                    onRequestSignOut = {}, onConfirmSignOut = { signOuts++ },
                    onCancelSignOut = { cancels++ },
                )
            }
        }
        rule.onNodeWithText(context.getString(R.string.account_sign_out_confirm, 35)).assertExists()
        rule.onNodeWithText(context.getString(R.string.album_refresh_cancel)).performClick()
        rule.runOnIdle {
            assertEquals(1, cancels)
            assertEquals(0, signOuts)
        }
    }
}
