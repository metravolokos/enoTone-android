package app.enotone.library

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedTagIndexSessionTest {
    private fun batch(attempted: Int = 52, gets: Int = 60,
                      bytes: Long = 3932160L, remaining: Int = 3400,
                      stop: String = "NETWORK_BUDGET") =
        PilotIndexProgress(100, attempted, attempted, 0, 0, 0,
            gets, bytes, remaining, stop)

    @Test fun fourPassesNeverExceedSessionRequestOrByteCaps() = runBlocking {
        var calls = 0
        val session = ManagedTagIndexSession { _, _, gate, onProgress ->
            calls++
            assertEquals(null, gate())
            val p = batch(remaining = 3590 - 62 - calls * 52)
            onProgress(p)
            p
        }
        val progress = session.run("synthetic@example.test", "synthetic-password", { true })
        assertEquals(4, calls)
        assertEquals(4, progress.completedBatches)
        assertEquals(208, progress.attempted)
        assertEquals(208, progress.savedWithTags)
        assertEquals(240, progress.getRequests)
        assertEquals(4 * 3932160L, progress.downloadedBytes)
        assertEquals("SESSION_LIMIT", progress.stopCode)
        assertTrue(progress.getRequests <= ManagedIndexLimits.MAX_TOTAL_GETS)
        assertTrue(progress.downloadedBytes <= ManagedIndexLimits.MAX_TOTAL_BYTES)
    }

    @Test fun wifiPolicyFailureBeforeBatchMeansZeroRequests() = runBlocking {
        var calls = 0
        val session = ManagedTagIndexSession { _, _, _, _ ->
            calls++
            batch()
        }
        val result = session.run("synthetic@example.test", "synthetic-password", { false })
        assertEquals(0, calls)
        assertEquals(0, result.getRequests)
        assertEquals("NETWORK_POLICY", result.stopCode)
    }

    @Test fun transientNetworkAndServerErrorsStopSessionRatherThanRetryLoop() = runBlocking {
        for (stop in listOf("RETRY_BACKOFF", "AUTH_REJECTED", "NETWORK_POLICY")) {
            var calls = 0
            val session = ManagedTagIndexSession { _, _, _, _ ->
                calls++
                batch(attempted = 1, gets = 1, bytes = 65536, stop = stop)
            }
            val result = session.run("synthetic@example.test", "synthetic-password", { true })
            assertEquals(1, calls)
            assertEquals(stop, result.stopCode)
        }
    }

    @Test fun switchingNetworkPolicyBetweenBatchesHaltsAfterCheckpoints() = runBlocking {
        var calls = 0
        var allowed = true
        val session = ManagedTagIndexSession { _, _, _, callback ->
            calls++
            val progress = batch(remaining = 3528 - calls)
            callback(progress)
            allowed = false
            progress
        }
        val result = session.run("synthetic@example.test", "synthetic-password", { allowed })
        assertEquals(1, calls)
        assertEquals(52, result.savedWithTags)
        assertEquals("NETWORK_POLICY", result.stopCode)
    }

    @Test fun noCompletedFilesWithSpentNetworkBudgetIsNotNoEligibleFiles() = runBlocking {
        var calls = 0
        val session = ManagedTagIndexSession { _, _, _, _ ->
            calls++
            batch(attempted = 0, gets = 60, bytes = 3932160,
                remaining = 3506, stop = "NETWORK_BUDGET")
        }
        val result = session.run("synthetic@example.test", "synthetic-password", { true })
        assertEquals(4, calls)
        assertEquals("SESSION_LIMIT", result.stopCode)
        assertEquals(240, result.getRequests)
    }

    @Test fun noEligibleCandidatesDoNotSpinAcrossBatches() = runBlocking {
        var calls = 0
        val session = ManagedTagIndexSession { _, _, _, _ ->
            calls++
            batch(attempted = 0, gets = 0, bytes = 0, remaining = 0, stop = "COMPLETED")
        }
        val result = session.run("synthetic@example.test", "synthetic-password", { true })
        assertEquals(1, calls)
        assertEquals("NO_ELIGIBLE_FILES", result.stopCode)
    }
}
