package app.enotone.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ManagedIndexSettingsViewModelTest {
    private class Fake : ManagedIndexSettingsGateway {
        var continuous = false
        var starts = 0
        var pauses = 0
        var stops = 0
        override fun start(email: String, password: String, continuous: Boolean): Boolean {
            starts++
            this.continuous = continuous
            return email.isNotBlank() && password.isNotBlank()
        }
        override fun pause() { pauses++ }
        override fun stop() { stops++ }
    }

    @Test fun startPauseStopRemainOnlyGatewayCommands() {
        val fake = Fake()
        val model = ManagedIndexSettingsViewModel(fake)
        model.start("valid@invalid.test", "secret", true)
        assertEquals(true, fake.continuous)
        assertEquals(1, fake.starts)
        assertEquals(true, model.state.value.lastStartSucceeded)
        model.pause()
        model.stop()
        assertEquals(1, fake.pauses)
        assertEquals(1, fake.stops)
    }
}
