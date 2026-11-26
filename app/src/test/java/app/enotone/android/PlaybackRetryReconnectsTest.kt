package app.enotone.android

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Retry" in the playback error sheet must actually try to reconnect.
 *
 * When `bindService` returns false the connection publishes `ERROR`, `service` stays null and
 * the sheet is shown. `retryCurrent()` used to return false there, so the sheet could not even
 * be dismissed; returning true alone is not enough either, because `connect()` returns early
 * while the ticker from the first `connect()` is alive. This pins the observable effect: the
 * tap causes another `bindService` call.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackRetryReconnectsTest {
    /** Counts binds and lets the test decide whether each one succeeds. */
    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var binds = 0
        var bindingSucceeds = false
        override fun bindService(
            service: Intent,
            conn: ServiceConnection,
            flags: Int,
        ): Boolean {
            binds++
            return bindingSucceeds
        }

        override fun unbindService(conn: ServiceConnection) = Unit
    }

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `retry after a failed bind binds again instead of doing nothing`() {
        val context = RecordingContext(application)
        val connection = AndroidPlaybackConnection(context, UnconfinedTestDispatcher())
        try {
            connection.connect()
            assertEquals("the first connect must bind", 1, context.binds)
            assertEquals("a failed bind is reported as an error", "ERROR",
                connection.state.value.playbackState)

            // The service is allowed to come up this time.
            context.bindingSucceeds = true
            val retried = connection.retryCurrent()

            assertTrue("retry must report that it acted", retried)
            assertEquals("retry must call bindService again", 2, context.binds)
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `retry reports failure when it cannot even bind`() {
        val context = RecordingContext(application)
        val connection = AndroidPlaybackConnection(context, UnconfinedTestDispatcher())
        try {
            connection.connect()
            val retried = connection.retryCurrent()
            // Nothing could be done: the sheet must stay open rather than claim success.
            assertEquals(false, retried)
            assertEquals("it still tried", 2, context.binds)
        } finally {
            connection.disconnect()
        }
    }
}
