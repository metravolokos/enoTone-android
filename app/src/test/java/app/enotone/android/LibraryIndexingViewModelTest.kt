package app.enotone.android

import app.enotone.library.ManagedIndexMode
import app.enotone.network.FlatWebDavHttpError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryIndexingViewModelTest {
    private class Gateway : LibraryIndexingGateway {
        var updates = 0
        var reindexes = 0
        var pending = 3
        var wait: CompletableDeferred<Unit>? = null
        var fail = false
        var httpFail = false
        override suspend fun update(login: StoredWebDavLogin): LibraryImportResult {
            updates++
            wait?.await()
            if (fail) error("private exception")
            if (httpFail) throw FlatWebDavHttpError(503)
            return LibraryImportResult("IMPORTED", pending)
        }
        override suspend fun reindex(login: StoredWebDavLogin): Int {
            reindexes++
            return 3
        }
    }
    private class Fixture {
        val gateway = Gateway()
        val launcher = FakeBackgroundWorkLauncher()
        val session = SettingsWorkSession()
        val model = LibraryIndexingViewModel(gateway, launcher, session, testSettingsText)
        fun signIn() = session.updateLogin(testLogin)
    }

    @Test fun `update without login reports error and does not launch work`() = settingsTest {
        val fixture = Fixture()
        fixture.model.updateLibrary()
        advanceUntilIdle()
        assertEquals(0, fixture.gateway.updates)
        assertEquals(0, fixture.launcher.indexingStarts)
        assertEquals(testSettingsText.get(R.string.missing_credentials), fixture.model.state.value.report)
    }

    @Test fun `incremental update commits before one service start and ignores repeated clicks`() = settingsTest {
        val fixture = Fixture()
        fixture.signIn()
        fixture.gateway.wait = CompletableDeferred()
        runCurrent()
        fixture.model.updateLibrary()
        fixture.model.updateLibrary()
        runCurrent()
        assertEquals(1, fixture.gateway.updates)
        assertEquals(0, fixture.launcher.indexingStarts)
        assertEquals(0, fixture.gateway.reindexes)
        fixture.gateway.wait?.complete(Unit)
        advanceUntilIdle()
        assertTrue(fixture.model.state.value.permissionPending)
        fixture.model.updateLibrary()
        fixture.model.onNotificationPermission(true)
        fixture.model.onNotificationPermission(true)
        advanceUntilIdle()
        fixture.model.updateLibrary()
        assertEquals(1, fixture.launcher.indexingStarts)
        assertEquals(ManagedIndexMode.CONTINUOUS, fixture.launcher.mode)
        assertEquals(1, fixture.gateway.updates)
        assertEquals("IMPORTED", fixture.model.state.value.report)
    }

    @Test fun `full reindex cannot run before confirmation and cancel is harmless`() = settingsTest {
        val fixture = Fixture()
        fixture.signIn()
        advanceUntilIdle()
        fixture.model.confirmReindex()
        fixture.model.showReindex(true)
        assertTrue(fixture.model.state.value.confirmReindex)
        fixture.model.showReindex(false)
        fixture.model.confirmReindex()
        advanceUntilIdle()
        assertEquals(0, fixture.gateway.reindexes)
        assertEquals(0, fixture.launcher.indexingStarts)
        fixture.model.showReindex(true)
        fixture.model.confirmReindex()
        advanceUntilIdle()
        fixture.model.onNotificationPermission(true)
        assertEquals(1, fixture.gateway.reindexes)
        assertEquals(1, fixture.launcher.indexingStarts)
    }

    @Test fun `no pending tags needs no reader and denied permission starts nothing`() = settingsTest {
        val fixture = Fixture()
        fixture.signIn()
        fixture.gateway.pending = 0
        advanceUntilIdle()
        fixture.model.updateLibrary()
        advanceUntilIdle()
        assertFalse(fixture.model.state.value.permissionPending)
        fixture.model.resume()
        fixture.model.onNotificationPermission(false)
        assertTrue(fixture.model.state.value.permissionDenied)
        assertEquals(0, fixture.launcher.indexingStarts)
    }

    @Test fun `sign out cancels import and invalidates pending permission`() = settingsTest {
        val fixture = Fixture()
        fixture.signIn()
        advanceUntilIdle()
        fixture.model.resume()
        fixture.session.updateLogin(null)
        fixture.model.onNotificationPermission(true)
        advanceUntilIdle()
        assertEquals(0, fixture.launcher.indexingStarts)
        fixture.signIn()
        advanceUntilIdle()
        fixture.gateway.wait = CompletableDeferred()
        fixture.model.updateLibrary()
        runCurrent()
        fixture.session.updateLogin(null)
        runCurrent()
        fixture.gateway.wait?.complete(Unit)
        advanceUntilIdle()
        assertFalse(fixture.model.state.value.permissionPending)
        assertFalse(fixture.model.state.value.busy)
        assertEquals(0, fixture.launcher.indexingStarts)
    }

    @Test fun `failure releases busy gate and pause stop go through launcher`() = settingsTest {
        val fixture = Fixture()
        fixture.signIn()
        fixture.gateway.fail = true
        advanceUntilIdle()
        fixture.model.updateLibrary()
        advanceUntilIdle()
        assertFalse(fixture.model.state.value.busy)
        assertEquals(0, fixture.launcher.indexingStarts)
        fixture.model.control(WorkAction.PAUSE)
        fixture.model.control(WorkAction.STOP)
        assertEquals(listOf(WorkAction.PAUSE, WorkAction.STOP), fixture.launcher.indexingActions)
    }

    @Test fun `resume continues in continuous mode and is blocked while running`() = settingsTest {
        val fixture = Fixture()
        fixture.signIn()
        advanceUntilIdle()
        fixture.launcher.indexing.value = ManagedScanStatus(ManagedScanState.RUNNING)
        fixture.model.resume()
        assertFalse(fixture.model.state.value.permissionPending)
        fixture.launcher.indexing.value = ManagedScanStatus(ManagedScanState.ERROR)
        fixture.model.resume()
        fixture.model.onNotificationPermission(true)
        assertEquals(1, fixture.launcher.indexingStarts)
        assertEquals(ManagedIndexMode.CONTINUOUS, fixture.launcher.mode)
    }

    @Test fun `network failure shows plain text without http code on the main screen`() = settingsTest {
        val fixture = Fixture()
        fixture.signIn()
        fixture.gateway.httpFail = true
        advanceUntilIdle()
        fixture.model.updateLibrary()
        advanceUntilIdle()
        val report = fixture.model.state.value.report
        assertEquals(testSettingsText.get(R.string.library_update_network_failed), report)
        assertFalse(report.contains("503"))
        assertFalse(fixture.model.state.value.busy)
    }
}
