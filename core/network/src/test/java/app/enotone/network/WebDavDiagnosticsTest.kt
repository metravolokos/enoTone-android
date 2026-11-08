package app.enotone.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavDiagnosticsTest {
    @Test fun reportsOnlyWhitelistedDataAndDoesNotFollowRedirects() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(207)
                .addHeader("ETag", "SECRET_ETAG_VALUE"))
            server.enqueue(MockResponse().setResponseCode(401)
                .addHeader("Location", "https://evil.example/private"))
            server.enqueue(MockResponse().setResponseCode(206).addHeader("Accept-Ranges", "bytes"))
            val report = WebDavDiagnostics(server.url("/")).inspect(
                "private-user@example.test", "SECRET_PASSWORD",
                "/AllMusic/Тест.mp3",
            )
            assertEquals(4, report.steps.size)
            assertEquals(207, report.steps[0].httpStatus)
            assertEquals(401, report.steps[1].httpStatus)
            assertEquals(206, report.steps[3].httpStatus)
            val sanitized = report.toSanitizedText()
            assertFalse(sanitized.contains("SECRET"))
            assertFalse(sanitized.contains("private-user"))
            assertFalse(sanitized.contains("Тест.mp3"))
            assertFalse(sanitized.contains("evil.example"))
            assertEquals("PROPFIND", server.takeRequest().method)
            assertEquals("/AllMusic", server.takeRequest().path)
            val ranged = server.takeRequest()
            assertEquals("bytes=0-0", ranged.getHeader("Range"))
            assertTrue(ranged.path!!.contains("%D0"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun skipsUnsafeOrMissingFilePath() {
        val server = MockWebServer()
        server.start()
        try {
            repeat(2) { server.enqueue(MockResponse().setResponseCode(207)) }
            val report = WebDavDiagnostics(server.url("/")).inspect("u", "p", "/AllMusic/../private.mp3")
            assertEquals(4, report.steps.size)
            assertEquals(null, report.steps[3].httpStatus)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
