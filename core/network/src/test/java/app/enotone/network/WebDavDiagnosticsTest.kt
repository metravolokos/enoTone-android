package app.enotone.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavDiagnosticsTest {
    @Test fun reportsOnlyWhitelistedDataAndDoesNotFollowRedirects() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse.Builder().code(207)
                .addHeader("ETag", "SECRET_ETAG_VALUE").build())
            server.enqueue(MockResponse.Builder().code(401)
                .addHeader("Location", "https://evil.example/private").build())
            server.enqueue(MockResponse.Builder().code(206).addHeader("Accept-Ranges", "bytes").build())
            val report = WebDavDiagnostics(server.url("/").toString(), allowLoopbackHttpForTests = true).inspect(
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
            assertEquals("/AllMusic", server.takeRequest().url.encodedPath)
            val ranged = server.takeRequest()
            assertEquals("bytes=0-0", ranged.headers["Range"])
            assertTrue(ranged.url.encodedPath.contains("%D0"))
        } finally {
            server.close()
        }
    }

    @Test fun skipsUnsafeOrMissingFilePath() {
        val server = MockWebServer()
        server.start()
        try {
            repeat(2) { server.enqueue(MockResponse.Builder().code(207).build()) }
            val report = WebDavDiagnostics(server.url("/").toString(), allowLoopbackHttpForTests = true)
                .inspect("u", "p", "/AllMusic/../private.mp3")
            assertEquals(4, report.steps.size)
            assertEquals(null, report.steps[3].httpStatus)
            assertEquals(2, server.requestCount)
        } finally {
            server.close()
        }
    }

    @Test fun rejectsCleartextExceptExplicitLoopbackTest() {
        val server = MockWebServer()
        server.start()
        try {
            var rejected = false
            try {
                WebDavDiagnostics(server.url("/").toString())
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
        } finally {
            server.close()
        }
    }
}
