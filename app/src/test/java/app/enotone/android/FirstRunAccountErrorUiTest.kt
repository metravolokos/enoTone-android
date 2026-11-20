package app.enotone.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.enotone.android.core.ui.theme.EnoToneTheme as EnoTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w377dp-h816dp-xxxhdpi")
class FirstRunAccountErrorUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dismissedLoginErrorsStayHiddenUntilANewFailure() {
        val accountModel = AccountSettingsViewModel(FakeAccountGateway(
            AccountVerificationResult.CREDENTIALS_REJECTED,
            AccountVerificationResult.CLOUD_UNAVAILABLE,
            AccountVerificationResult.CLOUD_UNAVAILABLE,
            AccountVerificationResult.CLOUD_UNAVAILABLE,
        ))
        rule.setContent { EnoTheme(darkTheme = false) { FirstRunLoginErrorHarness(accountModel) } }

        waitForRestore(accountModel)
        enterCredentials()
        submitAndWait(accountModel, AccountError.CREDENTIALS_REJECTED, CREDENTIALS_ERROR)

        rule.onNodeWithText("Ввести заново").performClick()
        assertDismissed(accountModel, CREDENTIALS_ERROR)
        rule.runOnIdle { assertEquals("bandit@example.invalid", accountModel.state.value.email) }
        val emailField = rule.onNodeWithContentDescription("Почта")
        emailField.assertIsEnabled().performTextInput(".edited")
        rule.runOnIdle { assertEquals("bandit@example.invalid.edited", accountModel.state.value.email) }

        submitAndWait(accountModel, AccountError.CONNECTION, CLOUD_ERROR)
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        assertDismissed(accountModel, CLOUD_ERROR)

        submitAndWait(accountModel, AccountError.CONNECTION, CLOUD_ERROR)
        // The area above the modal sheet is the outside-dismiss target.
        rule.onAllNodes(isDialog())[0].performTouchInput {
            click(Offset(center.x, top + 50f))
        }
        assertDismissed(accountModel, CLOUD_ERROR)

        submitAndWait(accountModel, AccountError.CONNECTION, CLOUD_ERROR)
    }

    @Composable
    private fun FirstRunLoginErrorHarness(accountModel: AccountSettingsViewModel) {
        val account by accountModel.state.collectAsState()
        var firstRunError by remember { mutableStateOf<FirstRunError?>(null) }
        FirstRunAccountErrorEffect(account.error, FirstRunPhase.LOGIN, firstRunError) {
            firstRunError = it
        }
        FirstRunExperience(
            phase = FirstRunPhase.LOGIN,
            account = account,
            indexing = LibraryIndexingUiState(),
            songCount = 0,
            albumCount = 0,
            artistCount = 0,
            error = firstRunError,
            onEmailChange = accountModel::onEmailChanged,
            onPasswordChange = accountModel::onPasswordChanged,
            onConnect = accountModel::verifyConnection,
            onListenWhileReading = {},
            onRetryNow = {},
            onGoToMusic = {},
            onErrorAction = { error ->
                accountModel.dismissError()
                firstRunError = null
                if (error == FirstRunError.CLOUD_UNAVAILABLE) accountModel.verifyConnection()
            },
            onErrorDismiss = {
                accountModel.dismissError()
                firstRunError = null
            },
        )
    }

    private fun enterCredentials() {
        rule.onNodeWithContentDescription("Почта").performTextInput("bandit@example.invalid")
        rule.onNodeWithContentDescription("Пароль для внешнего приложения").performTextInput("bad-password")
    }

    private fun submitAndWait(model: AccountSettingsViewModel, expected: AccountError, text: String) {
        rule.onNodeWithText("Подключить").performClick()
        waitForError(model, expected, text)
    }

    private fun assertDismissed(model: AccountSettingsViewModel, text: String) {
        rule.waitForIdle()
        rule.onNodeWithText(text).assertDoesNotExist()
        rule.runOnIdle { assertNull(model.state.value.error) }
    }

    private fun waitForRestore(model: AccountSettingsViewModel) {
        rule.waitUntil(timeoutMillis = 10_000) { !model.state.value.restoring }
        rule.waitForIdle()
    }

    private fun waitForError(model: AccountSettingsViewModel, expected: AccountError, text: String) {
        rule.waitUntil(timeoutMillis = 10_000) {
            model.state.value.error == expected &&
                rule.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private class FakeAccountGateway(
        vararg results: AccountVerificationResult,
    ) : AccountSettingsGateway {
        private val results = results.toMutableList()

        override suspend fun restore(): StoredWebDavLogin? = null
        override suspend fun verify(email: String, password: String): Boolean = false
        override suspend fun verifyOutcome(email: String, password: String): AccountVerificationResult =
            results.removeAt(0)

        override suspend fun save(email: String, password: String) = Unit
        override suspend fun countTracks(email: String): Int = 0
        override suspend fun signOut(email: String) = Unit
    }

    private companion object {
        const val CREDENTIALS_ERROR =
            "Ура, ошибка: почта или пароль не подошли! Теперь можно немного отдохнуть."
        const val CLOUD_ERROR = "Облако не отвечает, вот и славно: я немного отдохну!"
    }
}
