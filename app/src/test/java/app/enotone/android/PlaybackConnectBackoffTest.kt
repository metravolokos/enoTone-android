package app.enotone.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A reconnect after a bind timeout must back off instead of re-binding every three seconds.
 *
 * `PlaybackConnection.bindNow` waits [CONNECT_TIMEOUT_MS] and then calls `reattach()` when the
 * service still is not there. Without a growing pause a service that never connects is
 * re-bound at a fixed rate for as long as the app stays open, which is exactly the dead end
 * #258 describes — the retry has to be bounded in rate, not abandoned or unbounded.
 */
class PlaybackConnectBackoffTest {
    @Test
    fun `the first attempt is immediate`() {
        assertEquals(0L, connectBackoffMs(0))
    }

    @Test
    fun `repeated failures grow the pause`() {
        val first = connectBackoffMs(1)
        val second = connectBackoffMs(2)
        val third = connectBackoffMs(3)
        assertTrue("the pause must grow, not shrink", first < second && second < third)
        assertTrue("the first pause must be noticeable", first >= 1_000L)
    }

    @Test
    fun `the pause is capped so retries keep happening`() {
        val cap = connectBackoffMs(Int.MAX_VALUE - 1)
        assertEquals("past the table the last value is reused", cap, connectBackoffMs(99))
        assertTrue("a retry must still happen, just rarely", cap <= 60_000L)
        assertEquals("negative attempts are treated as the first one", connectBackoffMs(0), connectBackoffMs(-5))
    }
}
