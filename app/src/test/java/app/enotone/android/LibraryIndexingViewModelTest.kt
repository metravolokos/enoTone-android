package app.enotone.android

import app.enotone.library.ManagedIndexMode
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
        override suspend fun update(login: StoredWebDavLogin): LibraryImportResult {
            updates++
            wait?.await()
            if (fail) error("private exception")
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
        fixture.model.requestReindex()
        assertTrue(fixture.model.state.value.confirmReindex)
        fixture.model.cancelReindex()
        fixture.model.confirmReindex()
        advanceUntilIdle()
        assertEquals(0, fixture.gateway.reindexes)
        assertEquals(0, fixture.launcher.indexingStarts)
        fixture.model.requestReindex()
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
        fixture.model.start(false)
        fixture.model.onNotificationPermission(false)
        assertTrue(fixture.model.state.value.permissionDenied)
        assertEquals(0, fixture.launcher.indexingStarts)
    }

    @Test fun `sign out cancels import and invalidates pending permission`() = settingsTest {
        val fixture = Fixture()
        fixture.signIn()
        advanceUntilIdle()
        fixture.model.start(true)
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
        fixture.model.pause()
        fixture.model.stop()
        assertEquals(listOf(WorkAction.PAUSE, WorkAction.STOP), fixture.launcher.indexingActions)
    }
}
