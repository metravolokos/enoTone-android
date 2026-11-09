package app.enotone.library

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ParallelTagPolicyTest {
    @Test fun batchStageFailureHasOnlyFixedNonSensitiveMessage() {
        val cause = IllegalStateException("https://private.example/AllMusic/secret.mp3")
        val wrapped = TagBatchStageException("DB_WRITE", cause)
        assertEquals("DB_WRITE", wrapped.phase)
        assertEquals("TAG_BATCH_STAGE_DB_WRITE", wrapped.message)
        assertFalse(wrapped.message.orEmpty().contains("private"))
        assertEquals(cause, wrapped.cause)
    }

    @Test fun parallelismAndRetriesAreBounded() {
        assertEquals(4, ParallelTagPolicy.WORKERS)
        assertEquals(2, ParallelTagPolicy.MAX_RETRIES)
        assertEquals(1_000L, ParallelTagPolicy.retryDelayMs(0, false))
        assertEquals(3_000L, ParallelTagPolicy.retryDelayMs(1, false))
        assertEquals(8_000L, ParallelTagPolicy.retryDelayMs(0, true))
        assertEquals(16_000L, ParallelTagPolicy.retryDelayMs(1, true))
    }

    @Test fun sharedQuotaNeverExceedsSixtyGetsOrFourMibUnderHeavyContention() {
        val quota = SharedRangeBudget(60, 4L * 1024L * 1024L)
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val futures = (0 until 500).map {
            executor.submit<Boolean> {
                start.await()
                quota.tryReserve(65536)
            }
        }
        try {
            start.countDown()
            assertEquals(60, futures.count { it.get(10, TimeUnit.SECONDS) })
            assertEquals(60, quota.reservedRequests())
            assertEquals(60L * 65536L, quota.reservedBytes())
            assertFalse(quota.canStartFile())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun failedRangesAlsoConsumeAReservationAndCannotOvershoot() {
        val quota = SharedRangeBudget(3, 3L * 65536L)
        assertTrue(quota.tryReserve(65536))
        assertTrue(quota.tryReserve(65536))
        assertTrue(quota.tryReserve(65536))
        assertFalse(quota.tryReserve(65536))
        assertEquals(3, quota.reservedRequests())
        assertEquals(196608L, quota.reservedBytes())
    }

    @Test fun networkGatePreventsASecondConcurrentRangeAfterBudgetReached() {
        val server = MockWebServer()
        val block = ByteArray(65536) { 7 }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(206)
                    .addHeader("Content-Range", "bytes=0-65535/65536")
                    .setBody(Buffer().write(block))
        }
        server.start()
        val quota = SharedRangeBudget(1, 65536L)
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val tasks = (0..1).map { n ->
                executor.submit<Boolean> {
                    start.await()
                    try {
                        WebDavRangeReader(
                            server.url("/AllMusic/").toString(),
                            "/AllMusic/synthetic_$n.mp3", 65536L,
                            "user@example.test", "test-password", null, true,
                            sharedBudget = quota,
                        ).use {
                            it.readAt(0L, ByteArray(1), 0, 1)
                            true
                        }
                    } catch (_: IOException) { false }
                }
            }
            start.countDown()
            assertEquals(1, tasks.count { it.get(10, TimeUnit.SECONDS) })
            assertEquals(1, server.requestCount)
            assertEquals(1, quota.reservedRequests())
        } finally {
            executor.shutdownNow()
            server.shutdown()
        }
    }
}
