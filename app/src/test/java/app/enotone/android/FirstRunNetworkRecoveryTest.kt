package app.enotone.android

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.enotone.android.core.ui.theme.EnoToneTheme
import app.enotone.library.ManagedIndexProgress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w377dp-h816dp-xxxhdpi")
class FirstRunNetworkRecoveryTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @After fun clearAccountState() {
        context.getSharedPreferences(ACCOUNT_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private class Gateway : LibraryIndexingGateway {
        var updateCalls = 0

        override suspend fun update(login: StoredWebDavLogin): LibraryImportResult {
            updateCalls++
            return LibraryImportResult("IMPORTED", pendingTags = 1, presentTracks = 1)
        }

        override suspend fun reindex(login: StoredWebDavLogin): Int = 0
    }

    @Test fun reconnectAutomaticallyRetriesFirstRunImport() {
        val network = FakeNetworkMonitor(initiallyAvailable = false)
        val gateway = Gateway()
        val session = SettingsWorkSession()
        val model = LibraryIndexingViewModel(
            gateway,
            FakeBackgroundWorkLauncher(),
            session,
            testSettingsText,
        )
        session.updateLogin(testLogin)
        val account = AccountUiState(active = testLogin, loggedIn = true, restoring = false)

        rule.setContent {
            val hasInternet by network.hasValidatedInternet.collectAsState()
            val indexing by model.state.collectAsState()
            val plan = FirstRunPhasePlanner.plan(
                FirstRunPlanningState(
                    signOutPending = false,
                    hasLogin = true,
                    firstRunCompleted = false,
                    indexingActive = indexing.status.active,
                    indexingComplete = indexing.status.state == ManagedScanState.COMPLETE,
                    libraryImportFinished = indexing.importVersion > 0,
                    songCount = indexing.importedTrackCount,
                    pendingTagCount = indexing.pendingTagCount,
                    hasValidatedInternet = hasInternet,
                ),
            )
            EnoToneTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    FirstRunImportEffect(
                        account = account,
                        indexing = indexing,
                        indexingModel = model,
                        plan = plan,
                        hasValidatedInternet = hasInternet,
                        onAutomaticRetry = {},
                    )
                }
            }
        }

        rule.waitForIdle()
        assertEquals(0, gateway.updateCalls)

        network.setAvailable(true)
        rule.waitForIdle()

        assertEquals(1, gateway.updateCalls)
        assertTrue(model.state.value.permissionPending)
    }

    @Test fun completedImportResumesAfterOfflineNetworkFailureWithoutRefreshingLibrary() {
        val fixture = recoveryFixture()
        prepareFailedOfflineScan(fixture)
        showFirstRunRoute(fixture)
        assertSleepingWithoutCloudError(fixture)
        reconnectAndAssertCheckpointResume(fixture)
    }

    private data class RecoveryFixture(
        val network: FakeNetworkMonitor,
        val gateway: Gateway,
        val launcher: FakeBackgroundWorkLauncher,
        val session: SettingsWorkSession,
        val model: LibraryIndexingViewModel,
        val accountModel: AccountSettingsViewModel,
        val preferences: FirstRunPreferences,
    )

    private fun recoveryFixture(): RecoveryFixture {
        val network = FakeNetworkMonitor(initiallyAvailable = false)
        val gateway = Gateway()
        val launcher = FakeBackgroundWorkLauncher()
        val session = SettingsWorkSession()
        val model = LibraryIndexingViewModel(gateway, launcher, session, testSettingsText)
        val accountModel = AccountSettingsViewModel(NoopAccountGateway())
        val preferences = FirstRunPreferences(context).apply { setFirstRunCompleted(false) }
        return RecoveryFixture(network, gateway, launcher, session, model, accountModel, preferences)
    }

    private fun prepareFailedOfflineScan(fixture: RecoveryFixture) {
        rule.runOnIdle {
            fixture.session.updateLogin(testLogin)
            fixture.model.updateLibrary(fromFirstRun = true)
        }
        rule.waitForIdle()
        assertEquals(1, fixture.gateway.updateCalls)
        assertTrue(fixture.model.state.value.importVersion > 0)
        assertTrue((fixture.model.state.value.pendingTagCount ?: 0) > 0)
        assertTrue(fixture.model.state.value.permissionPending)

        rule.runOnIdle { fixture.model.onNotificationPermission(granted = false) }
        rule.waitForIdle()
        assertEquals(1, fixture.launcher.indexingStarts)
        rule.runOnIdle {
            fixture.launcher.indexing.value = ManagedScanStatus(
                state = ManagedScanState.ERROR,
                progress = networkFailureProgress(),
            )
        }
        rule.waitForIdle()
    }

    private fun showFirstRunRoute(fixture: RecoveryFixture) {
        val account = AccountUiState(
            email = testLogin.email,
            active = testLogin,
            loggedIn = true,
            restoring = false,
        )
        rule.setContent {
            val hasInternet by fixture.network.hasValidatedInternet.collectAsState()
            val indexing by fixture.model.state.collectAsState()
            EnoToneTheme(darkTheme = true) {
                Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()) {
                    FirstRunRoute(
                        account = account,
                        accountModel = fixture.accountModel,
                        indexing = indexing,
                        indexingModel = fixture.model,
                        preferences = fixture.preferences,
                        signOutPending = false,
                        libraryState = null,
                        hasValidatedInternet = hasInternet,
                        appContent = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun assertSleepingWithoutCloudError(fixture: RecoveryFixture) {
        rule.onNodeWithText(context.getString(R.string.first_run_sleeping_title)).assertExists()
        rule.onNodeWithText(context.getString(R.string.first_run_error_cloud_unavailable)).assertDoesNotExist()
        assertEquals(1, fixture.launcher.indexingStarts)
    }

    private fun reconnectAndAssertCheckpointResume(fixture: RecoveryFixture) {
        fixture.network.setAvailable(true)
        rule.waitUntil(timeoutMillis = 10_000) { fixture.model.state.value.permissionPending }
        rule.runOnIdle { fixture.model.onNotificationPermission(granted = false) }
        rule.waitUntil(timeoutMillis = 10_000) {
            fixture.launcher.indexingStarts == 2 &&
                fixture.model.state.value.status.state == ManagedScanState.RUNNING
        }
        rule.waitForIdle()

        assertEquals(1, fixture.gateway.updateCalls)
        assertEquals(2, fixture.launcher.indexingStarts)
        rule.onNodeWithText(context.getString(R.string.first_run_indexing_title)).assertExists()
    }

    private class NoopAccountGateway : AccountSettingsGateway {
        override suspend fun restore(): StoredWebDavLogin? = null
        override suspend fun verify(email: String, password: String): Boolean = false
        override suspend fun save(email: String, password: String) = Unit
        override suspend fun countTracks(email: String): Int = 0
        override suspend fun signOut(email: String) = Unit
    }

    private fun networkFailureProgress() = ManagedIndexProgress(
        completedBatches = 1,
        attempted = 1,
        savedWithTags = 1,
        completedWithoutTags = 0,
        retryableErrors = 1,
        permanentErrors = 0,
        staleWrites = 0,
        getRequests = 1,
        downloadedBytes = 0,
        remaining = 3,
        stopCode = "RETRY_BACKOFF",
    )
}
