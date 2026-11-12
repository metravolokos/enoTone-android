package app.enotone.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkSettingsViewModelTest {
    private class Fake : ArtworkSettingsGateway {
        var current = ArtworkLoadMode.ON_DEMAND
        var bytes = 2048L
        var clearCalls = 0
        var stopped = 0
        var paused = 0
        var autoCalls = 0
        var failClear = false
        override fun mode() = current
        override fun setMode(value: ArtworkLoadMode) { current = value }
        override suspend fun bytes() = bytes
        override suspend fun clear() {
            clearCalls++
            if (failClear) throw IllegalStateException("storage unavailable")
            bytes = 0
        }
        override suspend fun resumeAuto(email: String, password: String) { autoCalls++ }
        override fun pause() { paused++ }
        override fun stop() { stopped++ }
    }

    @Test fun modeAndCacheActionsAreBackedByTheGateway() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = Fake()
            val vm = ArtworkSettingsViewModel(gateway)
            vm.changeMode(ArtworkLoadMode.AUTO_AFTER_IMPORT)
            assertEquals(ArtworkLoadMode.AUTO_AFTER_IMPORT, gateway.current)
            vm.maybeResume("valid@invalid.test", "secret", tagsBusy = false)
            advanceUntilIdle()
            assertEquals(1, gateway.autoCalls)
            vm.maybeResume("valid@invalid.test", "secret", tagsBusy = true)
            advanceUntilIdle()
            assertEquals(1, gateway.autoCalls)
            vm.refreshSize()
            advanceUntilIdle()
            assertEquals(2048L, vm.state.value.storedBytes)
            vm.clear()
            advanceUntilIdle()
            assertEquals(1, gateway.clearCalls)
            assertEquals(0L, vm.state.value.storedBytes)
            assertEquals(ArtworkClearFeedback.CLEARED, vm.state.value.feedback)
            assertFalse(vm.state.value.clearing)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failedClearIsReportedWithoutClaimingSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = Fake()
            gateway.failClear = true
            val vm = ArtworkSettingsViewModel(gateway)
            vm.clear()
            advanceUntilIdle()
            assertEquals(ArtworkClearFeedback.FAILED, vm.state.value.feedback)
            assertTrue(gateway.bytes > 0L)
            assertFalse(vm.state.value.clearing)
        } finally { Dispatchers.resetMain() }
    }
}
