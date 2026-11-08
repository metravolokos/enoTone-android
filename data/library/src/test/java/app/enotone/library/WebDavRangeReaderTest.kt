package app.enotone.library

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WebDavRangeReaderTest {
    private val payload = ByteArray(110_000) { (it % 251).toByte() }
    private fun reader(server: MockWebServer, path: String = "/AllMusic/sample.mp3") =
        WebDavRangeReader(server.url("/AllMusic/").toString(), path,
            payload.size.toLong(), "private@example.test", "secret",
            "\"v1\"", true)

    @Test fun rangeContentIsExactAndCachedAndEofSafe() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(req: RecordedRequest): MockResponse {
                val m = Regex("""bytes=(\d+)-(\d+)""").matchEntire(req.getHeader("Range")!!)!!
                val start = m.groupValues[1].toInt()
                val end = m.groupValues[2].toInt()
                return MockResponse().setResponseCode(206)
                    .addHeader("Content-Range", "bytes $start-$end/" + payload.size)
                    .addHeader("ETag", "\"v1\"")
                    .setBody(Buffer().write(payload, start, end - start + 1))
            }
        }
        server.start()
        try {
            reader(server).use { source ->
                val out = ByteArray(100)
                assertEquals(20, source.readAt(0, out, 5, 20))
                assertArrayEquals(payload.copyOfRange(0, 20), out.copyOfRange(5, 25))
                assertEquals(12, source.readAt(50, out, 0, 12))
                assertEquals(5, source.readAt(payload.size.toLong() - 5, out, 0, 12))
                assertEquals(-1, source.readAt(payload.size.toLong(), out, 0, 12))
                assertEquals(0, source.readAt(0, out, 0, 0))
                assertEquals(2, source.stats().requests)
                assertEquals(payload.size.toLong(), source.stats().downloadedBytes)
            }
            assertEquals(2, server.requestCount)
            val req = server.takeRequest()
            assertEquals("bytes=0-65535", req.getHeader("Range"))
            assertEquals("\"v1\"", req.getHeader("If-Range"))
            assertTrue(req.getHeader("Authorization")!!.startsWith("Basic "))
        } finally { server.shutdown() }
    }

    @Test fun refuses200And302WithoutFollowingRedirects() {
        for (code in listOf(200, 302)) {
            val server = MockWebServer()
            server.start()
            try {
                server.enqueue(MockResponse().setResponseCode(code)
                    .addHeader("Location", "http://evil.example/private"))
                reader(server).use { src ->
                    try { src.readAt(0, ByteArray(1), 0, 1); fail("Must reject") }
                    catch (e: IOException) {
                        assertFalse(e.message.orEmpty().contains("private"))
                        assertFalse(e.message.orEmpty().contains("secret"))
                        assertEquals(code, src.stats().httpStatus)
                        assertEquals(RangeReadIssue.HTTP_STATUS, src.stats().issue)
                    }
                }
                assertEquals(1, server.requestCount)
            } finally { server.shutdown() }
        }
    }

    @Test fun rejectsMismatchedContentRangeAndEtag() {
        for (badRange in listOf("bytes 1-65536/110000", "bytes 0-65535/110001")) {
            val server = MockWebServer()
            server.start()
            try {
                server.enqueue(MockResponse().setResponseCode(206)
                    .addHeader("Content-Range", badRange)
                    .setBody(Buffer().write(payload, 0, 65536)))
                reader(server).use { src ->
                    try { src.readAt(0, ByteArray(1), 0, 1); fail("Must reject") }
                    catch (_: IOException) {
                        assertEquals(RangeReadIssue.BAD_CONTENT_RANGE, src.stats().issue)
                        assertEquals(206, src.stats().httpStatus)
                    }
                }
            } finally { server.shutdown() }
        }
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(206)
                .addHeader("Content-Range", "bytes 0-65535/110000")
                .addHeader("ETag", "\"v2\"").setBody(Buffer().write(payload, 0, 65536)))
            reader(server).use { src ->
                try { src.readAt(0, ByteArray(1), 0, 1); fail("Must reject") }
                catch (_: IOException) {
                    assertEquals(RangeReadIssue.CHANGED_ETAG, src.stats().issue)
                }
            }
        } finally { server.shutdown() }
    }

    @Test fun refusesPathsOutsideFlatCloudFolder() {
        val server = MockWebServer()
        server.start()
        try {
            for (path in listOf("/AllMusic/nested/song.mp3", "/AllMusic/a%2Fb.mp3",
                "/AllMusic/%2E%2E", "/AllMusic/song.mp3?query=1")) {
                try { reader(server, path).close(); fail("Must reject") }
                catch (_: IllegalArgumentException) {}
            }
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun strictByteAndRequestLimit() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(req: RecordedRequest): MockResponse {
                val m = Regex("""bytes=(\d+)-(\d+)""").matchEntire(req.getHeader("Range")!!)!!
                return MockResponse().setResponseCode(206)
                    .addHeader("Content-Range", "bytes " + m.groupValues[1] + "-" +
                        m.groupValues[2] + "/16777216")
                    .setBody(Buffer().write(ByteArray(65536)))
            }
        }
        server.start()
        try {
            WebDavRangeReader(server.url("/AllMusic/").toString(), "/AllMusic/large.flac",
                16L * 1024 * 1024, "u@example.test", "secret", null, true).use { src ->
                val out = ByteArray(1)
                for (i in 0 until 128) assertEquals(1, src.readAt(i.toLong() * 65536, out, 0, 1))
                assertEquals(8L * 1024 * 1024, src.stats().downloadedBytes)
                try { src.readAt(128L * 65536, out, 0, 1); fail("Budget exceeded") }
                catch (_: IOException) {
                    assertEquals(RangeReadIssue.LIMIT, src.stats().issue)
                }
                assertEquals(128, server.requestCount)
            }
        } finally { server.shutdown() }
    }

    @Test fun classifiesContentRangeDifferencesWithoutExposingRawHeaders() {
        val cases = listOf(
            "bytes 0-65534/110000" to ContentRangeMismatchKind.END,
            "bytes 0-65535/110001" to ContentRangeMismatchKind.TOTAL,
            "bytes 1-65535/110000" to ContentRangeMismatchKind.START,
            "bytes 1-65536/110001" to ContentRangeMismatchKind.MULTIPLE,
            "bytes=0-65535/110000" to ContentRangeMismatchKind.FORMAT_EQUALS_SEPARATOR,
            "items 0-65535/110000" to ContentRangeMismatchKind.FORMAT_UNEXPECTED_UNIT,
            "bytes 0-65535/65535" to ContentRangeMismatchKind.INVALID_BOUNDS,
        )
        for ((header, expectedKind) in cases) {
            val server = MockWebServer()
            server.start()
            try {
                server.enqueue(MockResponse().setResponseCode(206)
                    .addHeader("Content-Range", header)
                    .setBody(Buffer().write(ByteArray(65536))))
                reader(server).use { source ->
                    try {
                        source.readAt(0, ByteArray(1), 0, 1)
                        fail("Unexpected successful Range for " + expectedKind)
                    } catch (_: IOException) {
                        val stats = source.stats()
                        assertEquals(RangeReadIssue.BAD_CONTENT_RANGE, stats.issue)
                        assertEquals(206, stats.httpStatus)
                        assertEquals(1, stats.requests)
                        assertEquals(0L, stats.downloadedBytes)
                        val mismatch = stats.rangeMismatch!!
                        assertEquals(expectedKind, mismatch.kind)
                        assertEquals(0L, mismatch.expectedStart)
                        assertEquals(65535L, mismatch.expectedEnd)
                        assertEquals(110000L, mismatch.expectedTotal)
                        // Only numbers and a fixed enum may be used in diagnostic output.
                        assertFalse(mismatch.toString().contains("example.test"))
                        assertFalse(mismatch.toString().contains("sample.mp3"))
                    }
                }
                assertEquals(1, server.requestCount)
            } finally {
                server.shutdown()
            }
        }
    }


    @Test fun acceptsRfc9110UnknownTotalOnlyWithExactBoundaries() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(req: RecordedRequest): MockResponse {
                val match = Regex("""bytes=(\\d+)-(\\d+)""")
                    .matchEntire(req.getHeader("Range")!!)!!
                val start = match.groupValues[1].toInt()
                val end = match.groupValues[2].toInt()
                return MockResponse().setResponseCode(206)
                    .addHeader("Content-Range", "bytes $start-$end/*")
                    .addHeader("ETag", "\\"v1\\"")
                    .setBody(Buffer().write(payload, start, end - start + 1))
            }
        }
        server.start()
        try {
            reader(server).use { source ->
                val out = ByteArray(4)
                assertEquals(4, source.readAt(0, out, 0, 4))
                assertArrayEquals(payload.copyOfRange(0, 4), out)
                assertEquals(4, source.readAt(65536L, out, 0, 4))
                assertEquals(2, source.stats().unknownTotalResponses)
                assertEquals(2, source.stats().requests)
                assertEquals(110000L, source.stats().downloadedBytes)
            }
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun rejectsWildcardTotalIfActualEndIsWrong() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(206)
                .addHeader("Content-Range", "bytes 0-65534/*")
                .setBody(Buffer().write(payload, 0, 65535)))
            reader(server).use { source ->
                try {
                    source.readAt(0, ByteArray(1), 0, 1)
                    fail("Wrong range must be rejected even with wildcard total")
                } catch (_: IOException) {
                    assertEquals(RangeReadIssue.BAD_CONTENT_RANGE, source.stats().issue)
                    assertEquals(ContentRangeMismatchKind.END, source.stats().rangeMismatch?.kind)
                    assertEquals(0L, source.stats().downloadedBytes)
                }
            }
        } finally { server.shutdown() }
    }

}
