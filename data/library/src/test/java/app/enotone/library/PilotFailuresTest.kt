package app.enotone.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic errors only; test fixtures never contain personal file paths or secrets. */
class PilotFailuresTest {
    @Test fun transientHttpErrorsCanBeRetriedByTheNextManualPilot() {
        for (status in listOf(408, 425, 429, 500, 502, 503, 599)) {
            val failure = PilotFailures.classify("PARSER",
                RangeReadStats(1, 0, status, RangeReadIssue.HTTP_STATUS))
            assertEquals("RETRY_HTTP", failure.code)
            assertEquals("RANGE", failure.stage)
            assertEquals("HTTP_STATUS", failure.reason)
            assertEquals(status, failure.httpStatus)
            assertFalse(failure.authRejected)
        }
    }

    @Test fun permanentHttpAndMalformedRangeMustNotLoopForever() {
        val absent = PilotFailures.classify("PARSER",
            RangeReadStats(1, 0, 404, RangeReadIssue.HTTP_STATUS))
        assertEquals("FAILED_HTTP", absent.code)
        assertFalse(absent.authRejected)

        val malformed = PilotFailures.classify("PARSER",
            RangeReadStats(1, 0, 206, RangeReadIssue.BAD_CONTENT_RANGE))
        assertEquals("FAILED_RANGE", malformed.code)
        assertEquals("BAD_CONTENT_RANGE", malformed.reason)
    }

    @Test fun authRejectionStopsPilotWithoutPersistingFailure() {
        for (status in listOf(401, 403)) {
            val failure = PilotFailures.classify("RANGE",
                RangeReadStats(1, 0, status, RangeReadIssue.HTTP_STATUS))
            assertTrue(failure.authRejected)
            assertEquals("RETRY_AUTH", failure.code)
            assertEquals(status, failure.httpStatus)
        }
    }

    @Test fun explicitMalformedAndOverBudgetParserResultsRemainVisible() {
        val stats = RangeReadStats(1, 65536, httpStatus = 206)
        val malformed = PilotFailures.fromParserResult(FastTagCode.INVALID_METADATA, stats)
        assertEquals("FAILED_METADATA", malformed?.code)
        assertEquals("INVALID_METADATA", malformed?.reason)
        assertEquals("PARSER", malformed?.stage)
        assertEquals(206, malformed?.httpStatus)
        val budget = PilotFailures.fromParserResult(FastTagCode.FAST_BUDGET_EXCEEDED, stats)
        assertEquals("FAILED_BUDGET", budget?.code)
        assertEquals("FAST_BUDGET_EXCEEDED", budget?.reason)
        assertEquals(null, PilotFailures.fromParserResult(FastTagCode.FLAC_VORBIS, stats))
    }

    /**
     * R-A7 defect (g). A refusal used to be indistinguishable from a completed
     * empty check: only INVALID_METADATA / FAST_BUDGET_EXCEEDED produced a
     * failure, so `UNSUPPORTED_LAYOUT` was recorded as "0 tags found" and never
     * retried. The parser verdict must survive into the index record.
     *
     * This is the one existing expectation the fix had to *change* rather than
     * add to (AGENTS.md §4): the previous `assertEquals(null, ...)` asserted the
     * very silent-failure behaviour R-A7 exists to remove.
     */
    @Test fun refusedLayoutsSurfaceAsFailedLayoutInsteadOfACompletedEmptyCheck() {
        val stats = RangeReadStats(1, 65536, httpStatus = 206)
        val refused = PilotFailures.fromParserResult(FastTagCode.UNSUPPORTED_LAYOUT, stats)
        assertEquals("FAILED_LAYOUT", refused?.code)
        assertEquals("UNSUPPORTED_LAYOUT", refused?.reason)
        assertEquals("PARSER", refused?.stage)
        assertEquals(206, refused?.httpStatus)
        val format = PilotFailures.fromParserResult(FastTagCode.UNSUPPORTED_FORMAT, stats)
        assertEquals("FAILED_LAYOUT", format?.code)
        assertEquals("UNSUPPORTED_FORMAT", format?.reason)
        assertEquals("PARSER", format?.stage)
        assertEquals(206, format?.httpStatus)
        // Absent tags and a successful parse still stay non-failures.
        assertEquals(null, PilotFailures.fromParserResult(FastTagCode.NO_EMBEDDED_TAGS, stats))
        assertEquals(null, PilotFailures.fromParserResult(FastTagCode.FLAC_VORBIS, stats))
    }

    @Test fun transportAndParserFailuresUseOnlyFixedSafeCodes() {
        val network = PilotFailures.classify("PARSER",
            RangeReadStats(2, 65536, issue = RangeReadIssue.NETWORK))
        assertEquals("RETRY_NETWORK", network.code)
        assertEquals("NETWORK", network.reason)
        assertEquals(0, network.httpStatus)

        val parser = PilotFailures.classify("PARSER", null)
        assertEquals("FAILED_PARSER", parser.code)
        assertEquals("PARSER_FAILURE", parser.reason)
        assertEquals("PARSER", parser.stage)
        assertEquals(0, parser.httpStatus)

        val preparation = PilotFailures.classify("RANGE", null)
        assertEquals("FAILED_PREPARATION", preparation.code)
        val budget = PilotFailures.classify("PARSER",
            RangeReadStats(6, 393216, issue = RangeReadIssue.LIMIT))
        assertEquals("FAILED_BUDGET", budget.code)
    }
}
