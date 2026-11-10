package app.enotone.library

import java.io.IOException
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
