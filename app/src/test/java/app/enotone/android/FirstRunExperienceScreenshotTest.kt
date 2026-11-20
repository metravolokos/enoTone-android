package app.enotone.android

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import app.enotone.library.ManagedIndexProgress
import java.text.NumberFormat
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.enotone.android.core.ui.theme.EnoToneTheme as EnoTheme

/** D6-FR first-run screens and issue-specified error copy at the shared 377 × 816 dp size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w377dp-h816dp-xxxhdpi")
class FirstRunExperienceScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @After fun clearAccountState() {
        context.getSharedPreferences(ACCOUNT_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun loginGraphite() = capture(FirstRunPhase.LOGIN, null, true, "login_graphite")

    @Test fun loginPorcelain() = capture(FirstRunPhase.LOGIN, null, false, "login_porcelain")

    @Test fun indexingGraphite() = capture(FirstRunPhase.INDEXING, null, true, "indexing_graphite")

    @Test fun indexingPorcelain() = capture(FirstRunPhase.INDEXING, null, false, "indexing_porcelain")

    @Test fun sleepingGraphite() = capture(
        FirstRunPhase.INDEXING_OFFLINE, null, true, "sleeping_graphite", indexingWithProgress(),
    )

    @Test fun sleepingPorcelain() = capture(
        FirstRunPhase.INDEXING_OFFLINE, null, false, "sleeping_porcelain", indexingWithProgress(),
    )

    @Test fun doneGraphite() = capture(FirstRunPhase.DONE, null, true, "done_graphite")

    @Test fun donePorcelain() = capture(FirstRunPhase.DONE, null, false, "done_porcelain")

    @Test fun loginCredentialsErrorGraphite() = capture(
        FirstRunPhase.LOGIN, FirstRunError.LOGIN_CREDENTIALS, true, "error_credentials_graphite",
    )

    @Test fun loginCredentialsErrorPorcelain() = capture(
        FirstRunPhase.LOGIN, FirstRunError.LOGIN_CREDENTIALS, false, "error_credentials_porcelain",
    )

    @Test fun cloudUnavailableErrorGraphite() = capture(
        FirstRunPhase.LOGIN, FirstRunError.CLOUD_UNAVAILABLE, true, "error_cloud_graphite",
    )

    @Test fun cloudUnavailableErrorPorcelain() = capture(
        FirstRunPhase.LOGIN, FirstRunError.CLOUD_UNAVAILABLE, false, "error_cloud_porcelain",
    )

    @Test fun expiredPasswordErrorGraphite() = capture(
        FirstRunPhase.INDEXING, FirstRunError.INDEXING_AUTHENTICATION, true, "error_auth_expired_graphite",
    )

    @Test fun expiredPasswordErrorPorcelain() = capture(
        FirstRunPhase.INDEXING, FirstRunError.INDEXING_AUTHENTICATION, false, "error_auth_expired_porcelain",
    )

    @Test fun unreadableFilesErrorGraphite() = capture(
        FirstRunPhase.INDEXING, FirstRunError.INDEXING_FILES, true, "error_files_graphite",
    )

    @Test fun unreadableFilesErrorPorcelain() = capture(
        FirstRunPhase.INDEXING, FirstRunError.INDEXING_FILES, false, "error_files_porcelain",
    )

    @Test fun listenWhileReadingPersistsCompletionBeforeLeavingFirstRun() {
        val preferences = FirstRunPreferences(context)
        preferences.setFirstRunCompleted(false)
        var completed = false
        rule.setContent {
            EnoTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    FirstRunExperience(
                        phase = FirstRunPhase.INDEXING,
                        account = AccountUiState(restoring = false),
                        indexing = LibraryIndexingUiState(),
                        songCount = 12, albumCount = 3, artistCount = 2, error = null,
                        onEmailChange = {}, onPasswordChange = {}, onConnect = {},
                        onListenWhileReading = { completeFirstRun(preferences) { completed = it } },
                        onRetryNow = {},
                        onGoToMusic = {}, onErrorAction = {}, onErrorDismiss = {},
                    )
                }
            }
        }
        rule.onNodeWithText("Слушать, пока читаю").performClick()
        rule.runOnIdle {
            assertTrue(completed)
            assertEquals(true, FirstRunPreferences(context).firstRunCompleted())
        }
    }

    @Test fun goToMusicPersistsCompletionBeforeLeavingDone() {
        val preferences = FirstRunPreferences(context)
        preferences.setFirstRunCompleted(false)
        var completed = false
        rule.setContent {
            EnoTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    FirstRunExperience(
                        phase = FirstRunPhase.DONE,
                        account = AccountUiState(restoring = false),
                        indexing = LibraryIndexingUiState(),
                        songCount = 12, albumCount = 3, artistCount = 2, error = null,
                        onEmailChange = {}, onPasswordChange = {}, onConnect = {},
                        onListenWhileReading = {},
                        onRetryNow = {},
                        onGoToMusic = { completeFirstRun(preferences) { completed = it } },
                        onErrorAction = {}, onErrorDismiss = {},
                    )
                }
            }
        }
        rule.onNodeWithText("К музыке").performClick()
        rule.runOnIdle {
            assertTrue(completed)
            assertEquals(true, FirstRunPreferences(context).firstRunCompleted())
        }
    }

    @Test fun networkSignalChangesPhasesBothWaysAndKeepsReadProgress() {
        val network = FakeNetworkMonitor(initiallyAvailable = true)
        val indexing = indexingWithProgress()
        val state = FirstRunPlanningState(
            signOutPending = false,
            hasLogin = true,
            firstRunCompleted = false,
            indexingActive = true,
            songCount = 3590,
            pendingTagCount = 2350,
        )
        val numberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU"))
        val progressMessage = context.getString(
            R.string.first_run_sleeping_message,
            numberFormat.format(1240),
            numberFormat.format(3590),
        )
        val indexingProgressMessage = context.getString(
            R.string.first_run_progress,
            numberFormat.format(1240),
            numberFormat.format(3590),
        )

        rule.setContent {
            val hasInternet by network.hasValidatedInternet.collectAsState()
            val phase = FirstRunPhasePlanner.plan(state.copy(hasValidatedInternet = hasInternet)).phase
            EnoTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    FirstRunExperience(
                        phase = phase,
                        account = AccountUiState(restoring = false),
                        indexing = indexing,
                        songCount = 3590,
                        albumCount = 12,
                        artistCount = 5,
                        error = null,
                        onEmailChange = {}, onPasswordChange = {}, onConnect = {},
                        onListenWhileReading = {}, onRetryNow = {}, onGoToMusic = {},
                        onErrorAction = {}, onErrorDismiss = {},
                    )
                }
            }
        }

        rule.onNodeWithText("Читаю вашу музыку…").assertExists()
        network.setAvailable(false)
        rule.waitForIdle()
        rule.onNodeWithText("Задремал — нет сети").assertExists()
        rule.onNodeWithText(progressMessage).assertExists()

        network.setAvailable(true)
        rule.waitForIdle()
        rule.onNodeWithText("Читаю вашу музыку…").assertExists()
        rule.onNodeWithText(indexingProgressMessage).assertExists()
    }

    @Test fun sleepingRetryInvokesImmediateAttemptCallback() {
        var retries = 0
        rule.setContent {
            EnoTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    FirstRunExperience(
                        phase = FirstRunPhase.INDEXING_OFFLINE,
                        account = AccountUiState(restoring = false),
                        indexing = indexingWithProgress(),
                        songCount = 3590,
                        albumCount = 12,
                        artistCount = 5,
                        error = null,
                        onEmailChange = {}, onPasswordChange = {}, onConnect = {},
                        onListenWhileReading = {}, onRetryNow = { retries++ }, onGoToMusic = {},
                        onErrorAction = {}, onErrorDismiss = {},
                    )
                }
            }
        }

        rule.onNodeWithText("Повторить сейчас").performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }

    private fun capture(
        phase: FirstRunPhase,
        error: FirstRunError?,
        dark: Boolean,
        name: String,
        indexing: LibraryIndexingUiState = LibraryIndexingUiState(),
    ) {
        rule.setContent {
            EnoTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    FirstRunExperience(
                        phase = phase,
                        account = AccountUiState(restoring = false, email = "bandit@example.invalid"),
                        indexing = indexing,
                        songCount = 84, albumCount = 12, artistCount = 5, error = error,
                        onEmailChange = {}, onPasswordChange = {}, onConnect = {},
                        onListenWhileReading = {}, onRetryNow = {}, onGoToMusic = {},
                        onErrorAction = {}, onErrorDismiss = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        error?.let { shownError ->
            val (message, action) = when (shownError) {
                FirstRunError.LOGIN_CREDENTIALS ->
                    "Ура, ошибка: почта или пароль не подошли! Теперь можно немного отдохнуть." to "Ввести заново"
                FirstRunError.CLOUD_UNAVAILABLE ->
                    "Облако не отвечает, вот и славно: я немного отдохну!" to "Повторить"
                FirstRunError.INDEXING_AUTHENTICATION ->
                    "Пароль больше не подходит, а я как раз хотел отдохнуть!" to "Войти заново"
                FirstRunError.INDEXING_FILES ->
                    "Файлы не читаются: отличный повод немного отдохнуть!" to "Повторить"
            }
            rule.onNodeWithText(message, substring = false).assertExists()
            rule.onNodeWithText(action, substring = false).assertExists()
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/first_run_$name.png")
    }

    private fun indexingWithProgress() = LibraryIndexingUiState(
        status = ManagedScanStatus(
            state = ManagedScanState.RUNNING,
            progress = ManagedIndexProgress(
                completedBatches = 4,
                attempted = 1240,
                savedWithTags = 1238,
                completedWithoutTags = 2,
                retryableErrors = 0,
                permanentErrors = 0,
                staleWrites = 0,
                getRequests = 1240,
                downloadedBytes = 0,
                remaining = 2350,
                stopCode = "NONE",
            ),
        ),
        hasLogin = true,
        importVersion = 1,
        importedTrackCount = 3590,
        pendingTagCount = 2350,
    )
}
