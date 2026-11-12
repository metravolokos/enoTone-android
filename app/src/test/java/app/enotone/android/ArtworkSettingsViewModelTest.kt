package app.enotone.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkSettingsViewModelTest {
    private class Gateway : ArtworkSettingsGateway {
        var savedMode = ArtworkLoadMode.ON_DEMAND
        var ready = false
        var needs = true
        var bytes = 100L
        var clears = 0
        var wait: CompletableDeferred<Unit>? = null
        override fun mode() = savedMode
        override fun saveMode(mode: ArtworkLoadMode) { savedMode = mode }
        override suspend fun tagsReady(login: StoredWebDavLogin): Boolean {
            wait?.await()
            return ready
        }
        override suspend fun needsPreparation(login: StoredWebDavLogin) = needs
        override suspend fun storedBytes() = bytes
        override suspend fun clear() { clears++; bytes = 0 }
    }
    private class Fixture {
        val gateway = Gateway()
        val launcher = FakeBackgroundWorkLauncher()
        val session = SettingsWorkSession()
        val model = ArtworkSettingsViewModel(gateway, launcher, session, testSettingsText)
        init { session.updateLogin(testLogin) }
    }

    @Test fun `mode is persisted and on demand never auto starts`() = settingsTest {
        val fixture = Fixture()
        fixture.gateway.ready = true
        advanceUntilIdle()
        assertEquals(0, fixture.launcher.artworkStarts)
        fixture.model.changeMode(ArtworkLoadMode.AUTO_AFTER_IMPORT)
        advanceUntilIdle()
        assertEquals(ArtworkLoadMode.AUTO_AFTER_IMPORT, fixture.gateway.savedMode)
        assertEquals(1, fixture.launcher.artworkStarts)
        fixture.model.changeMode(ArtworkLoadMode.ON_DEMAND)
        assertEquals(listOf(WorkAction.STOP), fixture.launcher.artworkActions)
        assertEquals(ArtworkLoadMode.ON_DEMAND, fixture.model.state.value.mode)
    }

    @Test fun `auto waits for completed tags and starts only once after import`() = settingsTest {
        val fixture = Fixture()
        fixture.launcher.indexing.value = ManagedScanStatus(ManagedScanState.RUNNING)
        fixture.model.changeMode(ArtworkLoadMode.AUTO_AFTER_IMPORT)
        advanceUntilIdle()
        assertEquals(0, fixture.launcher.artworkStarts)
        fixture.launcher.indexing.value = ManagedScanStatus(ManagedScanState.COMPLETE)
        advanceUntilIdle()
        assertEquals(0, fixture.launcher.artworkStarts)
        fixture.gateway.ready = true
        fixture.launcher.indexing.value = ManagedScanStatus(ManagedScanState.RUNNING)
        runCurrent()
        fixture.launcher.indexing.value = ManagedScanStatus(ManagedScanState.COMPLETE)
        advanceUntilIdle()
        assertEquals(1, fixture.launcher.artworkStarts)
        fixture.model.changeMode(ArtworkLoadMode.AUTO_AFTER_IMPORT)
        assertEquals(1, fixture.launcher.artworkStarts)
    }

    @Test fun `terminating tags and already prepared artwork prevent automatic start`() = settingsTest {
        val fixture = Fixture()
        fixture.gateway.ready = true
        fixture.launcher.indexing.value = ManagedScanStatus(ManagedScanState.STOPPED, terminating = true)
        fixture.model.changeMode(ArtworkLoadMode.AUTO_AFTER_IMPORT)
        advanceUntilIdle()
        assertEquals(0, fixture.launcher.artworkStarts)
        fixture.gateway.needs = false
        fixture.launcher.indexing.value = ManagedScanStatus(ManagedScanState.COMPLETE)
        advanceUntilIdle()
        assertEquals(0, fixture.launcher.artworkStarts)
    }

    @Test fun `clear refreshes storage size and refuses running or terminating work`() = settingsTest {
        val fixture = Fixture()
        fixture.model.setVisible(true)
        advanceUntilIdle()
        assertEquals(100L, fixture.model.state.value.storedBytes)
        fixture.launcher.artwork.value = ArtworkScanStatus(ArtworkScanState.RUNNING)
        fixture.model.clear()
        advanceUntilIdle()
        assertEquals(0, fixture.gateway.clears)
        fixture.launcher.artwork.value = ArtworkScanStatus(ArtworkScanState.STOPPED, terminating = true)
        fixture.model.clear()
        advanceUntilIdle()
        assertEquals(0, fixture.gateway.clears)
        fixture.launcher.artwork.value = ArtworkScanStatus(ArtworkScanState.COMPLETE)
        fixture.model.clear()
        fixture.model.clear()
        advanceUntilIdle()
        assertEquals(1, fixture.gateway.clears)
        assertEquals(0L, fixture.model.state.value.storedBytes)
        assertFalse(fixture.model.state.value.clearBusy)
        assertTrue(fixture.model.state.value.feedback.isNotEmpty())
    }

    @Test fun `mode change during preparation cannot start auto work`() = settingsTest {
        val fixture = Fixture()
        fixture.gateway.ready = true
        fixture.gateway.wait = CompletableDeferred()
        fixture.model.changeMode(ArtworkLoadMode.AUTO_AFTER_IMPORT)
        runCurrent()
        fixture.model.changeMode(ArtworkLoadMode.ON_DEMAND)
        fixture.gateway.wait?.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, fixture.launcher.artworkStarts)
    }
}
