package app.enotone.library

import java.io.IOException
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class EmbeddedArtworkPayloadReaderTest {
    private fun synchsafe(value: Int) = byteArrayOf(
        (value ushr 21 and 127).toByte(),
        (value ushr 14 and 127).toByte(),
        (value ushr 7 and 127).toByte(),
        (value and 127).toByte(),
    )
    private fun int32(value: Int) = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(),
        (value ushr 8).toByte(), value.toByte(),
    )
    private fun mp3(picture: ByteArray): ByteArray {
        val apic = byteArrayOf(0) + "image/jpeg".toByteArray() +
            byteArrayOf(0, 3, 0) + picture
        val frame = "APIC".toByteArray() + int32(apic.size) + byteArrayOf(0, 0) + apic
        return "ID3".toByteArray() + byteArrayOf(3, 0, 0) +
            synchsafe(frame.size) + frame + ByteArray(1024)
    }
    private fun blockReader(bytes: ByteArray): (Long, ByteArray, Int, Int) -> Int =
        { pos, dest, offset, n ->
            if (pos < 0 || pos >= bytes.size) -1 else {
                val count = minOf(n, bytes.size - pos.toInt())
                System.arraycopy(bytes, pos.toInt(), dest, offset, count)
                count
            }
        }

    @Test fun mockWebDavArtworkUsesOnlyStrictRangeGets() {
        val expected = ByteArray(180_000) { (it % 247).toByte() }
        val fixture = mp3(expected)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(req: RecordedRequest): MockResponse {
                val range = req.getHeader("Range")
                    ?: return MockResponse().setResponseCode(400)
                val match = Regex("""bytes=(\\d+)-(\\d+)""").matchEntire(range)
                    ?: return MockResponse().setResponseCode(400)
                val start = match.groupValues[1].toInt()
                val end = match.groupValues[2].toInt()
                if (start < 0 || end >= fixture.size || end < start) {
                    return MockResponse().setResponseCode(416)
                }
                return MockResponse().setResponseCode(206)
                    .addHeader("Content-Range", "bytes=$start-$end/" + fixture.size)
                    .addHeader("ETag", "\\"test-image-v1\\"")
                    .setBody(Buffer().write(fixture, start, end - start + 1))
            }
        }
        server.start()
        try {
            WebDavRangeReader(
                libraryRoot = server.url("/AllMusic/").toString(),
                encodedPath = "/AllMusic/cover.mp3",
                fileSize = fixture.size.toLong(),
                email = "fixture@example.test", appPassword = "mock-secret",
                etag = "\\"test-image-v1\\"",
                allowLoopbackHttpForTests = true,
                maxRequests = 16,
                maxDownloadedBytes = 512L * 1024,
            ).use { source ->
                val result = EmbeddedArtworkPayloadReader().read(
                    "cover.mp3", source.fileSize, source::readAt)
                assertArrayEquals(expected, result)
                assertTrue(source.stats().requests in 1..4)
                assertTrue(source.stats().downloadedBytes <= 4L * 65536)
            }
            assertTrue(server.requestCount in 1..4)
        } finally {
            server.shutdown()
        }
    }

    @Test fun returnsOnlyJpegBytesNotId3MetadataOrAudio() {
        val payload = ByteArray(200_001) { (it % 251).toByte() }
        val file = mp3(payload)
        val result = EmbeddedArtworkPayloadReader().read(
            "play.mp3", file.size.toLong(), blockReader(file))
        assertArrayEquals(payload, result)
    }

    @Test fun missingArtworkDoesNotReadPayload() {
        val noId3 = ByteArray(1_000)
        val result = EmbeddedArtworkPayloadReader().read(
            "silent.mp3", noId3.size.toLong(), blockReader(noId3))
        assertNull(result)
    }

    @Test fun unsupportedFileHasNoNetworkRead() {
        val result = EmbeddedArtworkPayloadReader().read("play.wav", 1000) {
            _, _, _, _ -> fail("Unsupported extension must not read"); -1
        }
        assertNull(result)
    }

    @Test fun shortNetworkReadDoesNotReturnIncompleteImage() {
        val file = mp3(ByteArray(600))
        var reads = 0
        val source = blockReader(file)
        val result = EmbeddedArtworkPayloadReader().read(
            "test.mp3", file.size.toLong()) { pos, out, off, n ->
            reads++
            if (reads > 3) -1 else source(pos, out, off, minOf(n, 1))
        }
        assertNull(result)
    }

    @Test fun readerDoesNotSwallowUnexpectedProgrammerBugs() {
        val file = mp3(ByteArray(60))
        val result = EmbeddedArtworkPayloadReader().read(
            "a.mp3", file.size.toLong()) { _, _, _, _ ->
            throw IOException("Range unavailable")
        }
        assertNull(result)
    }

    @Test fun rejectsPayloadOverFourMegabytes() {
        val file = mp3(ByteArray(4 * 1024 * 1024 + 1))
        var readCalls = 0
        val source = blockReader(file)
        val result = EmbeddedArtworkPayloadReader().read(
            "large.mp3", file.size.toLong()) { pos, out, off, n ->
            readCalls++
            source(pos, out, off, n)
        }
        assertNull(result)
        // Headers and small APIC prefix only; do not stream a huge image.
        assertTrue(readCalls < 20)
    }
}
