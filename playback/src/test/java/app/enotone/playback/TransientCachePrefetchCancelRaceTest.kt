package app.enotone.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prefetch worker may pass the ticket check and only then publish its writer. A
 * `cancelNext()` landing inside that window bumps `nextGeneration`, so the guard must be
 * evaluated again after publication — otherwise the cancel finds nothing to cancel and the
 * download starts after the track changed or playback stopped.
 *
 * This covers [TransientCachePolicy.ticketStillValid] in isolation, which is the predicate the
 * post-publication re-check in `prefetchNext` calls. The entry check is still inline there, and
 * the call site itself is covered end-to-end by `PrefetchCancelDuringWindowTest` in `:app`.
 */
class TransientCachePrefetchCancelRaceTest {
    @Test
    fun `a fresh prefetch is allowed to start`() {
        assertTrue(
            TransientCachePolicy.ticketStillValid(
                closed = false, generation = 0L, currentTicket = 0L,
                nextGeneration = 0L, nextTicket = 0L,
            ),
        )
    }

    @Test
    fun `cancelNext invalidates a worker that already passed the first check`() {
        // Worker captured these tickets, then passed the entry check.
        val generation = 0L
        val currentTicket = 0L
        val nextTicket = 0L
        assertTrue(
            "precondition: the worker is allowed to start",
            TransientCachePolicy.ticketStillValid(
                closed = false, generation = generation, currentTicket = currentTicket,
                nextGeneration = nextTicket, nextTicket = nextTicket,
            ),
        )

        // cancelNext() lands between the check and the publication.
        val nextAfterCancel = nextTicket + 1

        assertFalse(
            "after cancelNext() the worker must not call cache()",
            TransientCachePolicy.ticketStillValid(
                closed = false, generation = generation, currentTicket = currentTicket,
                nextGeneration = nextAfterCancel, nextTicket = nextTicket,
            ),
        )
    }

    @Test
    fun `a new track invalidates a stale next ticket`() {
        assertFalse(
            TransientCachePolicy.ticketStillValid(
                closed = false, generation = 1L, currentTicket = 0L,
                nextGeneration = 0L, nextTicket = 0L,
            ),
        )
    }

    @Test
    fun `a closed cache never starts a download`() {
        assertFalse(
            TransientCachePolicy.ticketStillValid(
                closed = true, generation = 0L, currentTicket = 0L,
                nextGeneration = 0L, nextTicket = 0L,
            ),
        )
    }

    @Test
    fun `replacing the candidate invalidates the previous one`() {
        // prefetchNext() calls cancelNext() first, which bumps nextGeneration; the new
        // worker then captures the bumped value and is allowed to run.
        val bumped = 1L
        assertFalse(
            "the replaced worker must stop",
            TransientCachePolicy.ticketStillValid(
                closed = false, generation = 0L, currentTicket = 0L,
                nextGeneration = bumped, nextTicket = 0L,
            ),
        )
        assertTrue(
            "the replacing worker may start",
            TransientCachePolicy.ticketStillValid(
                closed = false, generation = 0L, currentTicket = 0L,
                nextGeneration = bumped, nextTicket = bumped,
            ),
        )
    }
}
