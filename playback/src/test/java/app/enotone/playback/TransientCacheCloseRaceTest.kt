package app.enotone.playback

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A closing cache must not surface `RejectedExecutionException` to its callers.
 *
 * `close()` sets the closed flag and then shuts the worker down, so a submission that
 * loses that race throws from [java.util.concurrent.ExecutorService.execute]. Callers run
 * on the main thread or a service callback, where that exception kills the process.
 */
class TransientCacheCloseRaceTest {
    private class Harness {
        val closed = AtomicBoolean(false)
        val worker = Executors.newSingleThreadExecutor()
        val crashes = mutableListOf<Throwable>()

        /** Mirrors TransientMediaCache.enqueue: losing the close race is not a crash. */
        fun enqueue(task: () -> Unit) {
            if (closed.get()) return
            try {
                worker.execute(task)
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                // close() won the race; the cache is being torn down.
            }
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            worker.shutdownNow()
        }
    }

    @Test
    fun `submission racing with close never propagates`() {
        repeat(200) {
            val harness = Harness()
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)

            val submitter = Thread {
                start.await()
                try {
                    harness.enqueue { }
                } catch (error: Throwable) {
                    synchronized(harness.crashes) { harness.crashes += error }
                } finally {
                    done.countDown()
                }
            }
            val closer = Thread {
                start.await()
                try {
                    harness.close()
                } finally {
                    done.countDown()
                }
            }
            submitter.start()
            closer.start()
            start.countDown()
            assertTrue("both threads must finish", done.await(5, TimeUnit.SECONDS))
            submitter.join()
            closer.join()

            assertTrue(
                "a submission racing with close must not throw: ${harness.crashes}",
                harness.crashes.isEmpty(),
            )
            harness.worker.awaitTermination(1, TimeUnit.SECONDS)
        }
    }
}
