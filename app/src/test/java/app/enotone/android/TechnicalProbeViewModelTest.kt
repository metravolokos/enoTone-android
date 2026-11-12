package app.enotone.android

import app.enotone.network.FlatWebDavHttpError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TechnicalProbeViewModelTest {
    private class Fake : TechnicalProbeGateway {
        var checked = 0
        var counted = 0
        var fail = false
        override suspend fun webDav(
            email: String, password: String, file: String, infinity: Boolean,
        ): String {
            checked++
            if (fail) throw IllegalStateException("network")
            return "PROPFIND: HTTP 207"
        }
        override suspend fun remoteCount(email: String, password: String): RemoteListingCounts {
            counted++
            return RemoteListingCounts(2, 0, 0, 0, 0)
        }
    }

    @Test fun successfulProbeAndCountReturnOnlySanitizedData() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fake = Fake()
            val vm = TechnicalProbeViewModel(fake)
            vm.check("valid@invalid.test", "secret", "", false)
            assertEquals(true, vm.state.value.busy)
            advanceUntilIdle()
            assertFalse(vm.state.value.busy)
            assertEquals(1L, vm.state.value.version)
            assertEquals(TechnicalProbeResult.Report("PROPFIND: HTTP 207"),
                vm.state.value.result)
            vm.count("valid@invalid.test", "secret")
            advanceUntilIdle()
            assertEquals(2L, vm.state.value.version)
            assertEquals(TechnicalProbeResult.Counts(RemoteListingCounts(2, 0, 0, 0, 0)),
                vm.state.value.result)
            assertEquals(1, fake.counted)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failedProbeNeverLeaksThrowableMessage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fake = Fake()
            fake.fail = true
            val vm = TechnicalProbeViewModel(fake)
            vm.check("valid@invalid.test", "secret", "", false)
            advanceUntilIdle()
            assertEquals(TechnicalProbeResult.FailedProbe, vm.state.value.result)
            assertFalse(vm.state.value.busy)
        } finally { Dispatchers.resetMain() }
    }
}
