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
