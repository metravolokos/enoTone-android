package app.enotone.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import app.enotone.android.core.ui.theme.EnoToneTheme
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
}
