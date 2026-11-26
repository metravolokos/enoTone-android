package app.enotone.android

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import java.util.concurrent.atomic.AtomicInteger

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

    @Test fun authRejectionDoesNotRetryOrHideTheErrorWhenNetworkReturns() {
        assertTerminalErrorDoesNotRetry(
            stopCode = "AUTH_REJECTED",
            errorResource = R.string.first_run_error_auth_expired,
        )
    }

    @Test fun fileErrorDoesNotRetryOrHideTheErrorWhenNetworkReturns() {
        assertTerminalErrorDoesNotRetry(
            stopCode = "FAILED_INTERNAL",
            errorResource = R.string.first_run_error_files,
        )
    }

    @Test fun pausedIndexingResumesWhenNetworkReturns() {
        // PAUSED means the run was interrupted before finishing. Refusing to resume left
        // the first-run screen frozen with progress on it: a dead end the user could not
        // escape. Resuming continues from the last saved checkpoint, so it is safe.
        val fixture = recoveryFixture()
        prepareFailedOfflineScan(fixture)
        rule.runOnIdle {
            fixture.launcher.indexing.value = ManagedScanStatus(
                state = ManagedScanState.PAUSED,
                progress = networkFailureProgress().copy(stopCode = "PAUSED"),
            )
        }
        rule.waitForIdle()
        showFirstRunRoute(fixture)

        fixture.network.setAvailable(true)
        // The retry path asks for the notification permission before starting, exactly
        // like the network-recovery path above. Grant it to reach the actual resume.
        rule.waitUntil(10_000) { fixture.model.state.value.permissionPending }
        rule.runOnIdle { fixture.model.onNotificationPermission(granted = false) }
        rule.waitForIdle()

        assertEquals("PAUSED must resume from its checkpoint", 2, fixture.launcher.indexingStarts)
    }

    @Test fun stoppedIndexingDoesNotResumeWhenNetworkReturns() {
        assertStoppedStatusDoesNotRetry(ManagedScanState.STOPPED, "STOPPED")
    }

    @Test fun recreatedFirstRunEffectResumesCheckpointInsteadOfReimporting() {
        val fixture = recoveryFixture()
        prepareFailedOfflineScan(fixture)
        val generation = mutableIntStateOf(0)
        val account = signedInAccount()

        rule.setContent {
            val indexing by fixture.model.state.collectAsState()
            key(generation.intValue) {
                EnoToneTheme(darkTheme = true) {
                    FirstRunImportEffect(
                        account = account,
                        indexing = indexing,
                        indexingModel = fixture.model,
                        plan = FirstRunPlan(FirstRunPhase.INDEXING),
                        hasValidatedInternet = true,
                        onAutomaticRetry = {},
                    )
                }
            }
        }
        rule.waitUntil(timeoutMillis = 10_000) { fixture.model.state.value.permissionPending }
        assertEquals(1, fixture.gateway.updateCalls)
        rule.runOnIdle { fixture.model.onNotificationPermission(granted = false) }
        rule.waitForIdle()
        assertEquals(2, fixture.launcher.indexingStarts)

        rule.runOnIdle {
            fixture.launcher.indexing.value = ManagedScanStatus(
                state = ManagedScanState.ERROR,
                progress = networkFailureProgress(),
            )
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            fixture.model.state.value.status.state == ManagedScanState.ERROR
        }
        rule.runOnIdle { generation.intValue++ }
        rule.waitForIdle()
        assertTrue(
            "recreate retry was not accepted: ui=${fixture.model.state.value}, " +
                "autoRetry=${canAutomaticallyRetryFirstRunImport(fixture.model.state.value)}",
            fixture.model.state.value.permissionPending,
        )
        assertEquals(1, fixture.gateway.updateCalls)
        rule.runOnIdle { fixture.model.onNotificationPermission(granted = false) }
        rule.waitForIdle()
        assertEquals(3, fixture.launcher.indexingStarts)

        rule.runOnIdle {
            fixture.launcher.indexing.value = ManagedScanStatus(
                state = ManagedScanState.PAUSED,
                progress = networkFailureProgress().copy(stopCode = "PAUSED"),
            )
        }
        rule.waitForIdle()
        rule.runOnIdle { generation.intValue++ }
        rule.waitForIdle()

        assertEquals(1, fixture.gateway.updateCalls)
        assertEquals(3, fixture.launcher.indexingStarts)
    }

    @Test fun liveTerminatingStateDoesNotConsumeNetworkRetryUntilServiceStops() {
        val fixture = recoveryFixture()
        prepareFailedOfflineScan(fixture)
        rule.runOnIdle {
            fixture.launcher.indexing.value = fixture.launcher.indexing.value.copy(terminating = true)
        }
        rule.waitForIdle()
        val uiSnapshot = mutableStateOf(
            fixture.model.state.value.copy(
                status = fixture.launcher.indexing.value.copy(terminating = false),
            ),
        )
        val retryCallbacks = AtomicInteger()
        val account = signedInAccount()

        rule.setContent {
            val hasInternet by fixture.network.hasValidatedInternet.collectAsState()
            FirstRunImportEffect(
                account = account,
                indexing = uiSnapshot.value,
                indexingModel = fixture.model,
                plan = FirstRunPlan(
                    if (hasInternet) FirstRunPhase.INDEXING else FirstRunPhase.INDEXING_OFFLINE,
                ),
                hasValidatedInternet = hasInternet,
                onAutomaticRetry = { retryCallbacks.incrementAndGet() },
            )
        }
        rule.waitForIdle()
        assertEquals(1, fixture.launcher.indexingStarts)

        fixture.network.setAvailable(true)
        rule.waitForIdle()
        assertEquals(0, retryCallbacks.get())
        assertEquals(1, fixture.launcher.indexingStarts)

        rule.runOnIdle {
            fixture.launcher.indexing.value = ManagedScanStatus(
                state = ManagedScanState.ERROR,
                progress = networkFailureProgress(),
            )
        }
        rule.waitUntil(timeoutMillis = 10_000) { !fixture.model.state.value.status.terminating }
        rule.runOnIdle {
            uiSnapshot.value = uiSnapshot.value.copy(operationFailureVersion = 1)
        }
        rule.waitForIdle()
        assertTrue(
            "recovery retry was not accepted: ui=${fixture.model.state.value}, " +
                "screen=${uiSnapshot.value}, autoRetry=${canAutomaticallyRetryFirstRunImport(uiSnapshot.value)}, " +
                "liveTerminating=${fixture.model.scanIsTerminating}",
            fixture.model.state.value.permissionPending,
        )
        assertEquals(1, retryCallbacks.get())
        rule.runOnIdle { fixture.model.onNotificationPermission(granted = false) }
        rule.waitForIdle()
        assertEquals(2, fixture.launcher.indexingStarts)
        assertEquals(1, fixture.gateway.updateCalls)
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

    private fun assertTerminalErrorDoesNotRetry(stopCode: String, errorResource: Int) {
        val fixture = recoveryFixture()
        prepareFailedOfflineScan(fixture)
        rule.runOnIdle {
            fixture.launcher.indexing.value = ManagedScanStatus(
                state = ManagedScanState.ERROR,
                progress = networkFailureProgress().copy(stopCode = stopCode),
            )
        }
        rule.waitForIdle()
        showFirstRunRoute(fixture)
        val errorText = context.getString(errorResource)
        rule.onNodeWithText(errorText).assertExists()

        fixture.network.setAvailable(true)
        rule.waitForIdle()

        assertEquals(1, fixture.launcher.indexingStarts)
        rule.onNodeWithText(errorText).assertExists()
    }

    private fun assertStoppedStatusDoesNotRetry(state: ManagedScanState, stopCode: String) {
        val fixture = recoveryFixture()
        prepareFailedOfflineScan(fixture)
        rule.runOnIdle {
            fixture.launcher.indexing.value = ManagedScanStatus(
                state = state,
                progress = networkFailureProgress().copy(stopCode = stopCode),
            )
        }
        rule.waitForIdle()
        showFirstRunRoute(fixture)

        fixture.network.setAvailable(true)
        rule.waitForIdle()

        assertEquals(1, fixture.launcher.indexingStarts)
    }

    private fun signedInAccount() = AccountUiState(
        email = testLogin.email,
        active = testLogin,
        loggedIn = true,
        restoring = false,
    )

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
