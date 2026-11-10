package app.enotone.library

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class EmbeddedArtworkLocatorTest {
    private fun be32(n: Int) = byteArrayOf(
        (n ushr 24).toByte(), (n ushr 16).toByte(),
        (n ushr 8).toByte(), n.toByte(),
    )

    private fun sync(n: Int) = byteArrayOf(
        ((n ushr 21) and 127).toByte(), ((n ushr 14) and 127).toByte(),
        ((n ushr 7) and 127).toByte(), (n and 127).toByte(),
    )

    private fun frame(name: String, body: ByteArray, version: Int) =
        name.toByteArray(Charsets.US_ASCII) +
            (if (version == 4) sync(body.size) else be32(body.size)) +
            byteArrayOf(0, 0) + body

    private fun mp3(version: Int, vararg frames: ByteArray): ByteArray {
        val payload = frames.fold(ByteArray(0)) { data, frame -> data + frame }
        return "ID3".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(version.toByte(), 0, 0) + sync(payload.size) +
            payload + ByteArray(256)
    }

    private fun apic(type: Int = 3, size: Int = 1000): ByteArray =
        byteArrayOf(0) + "image/jpeg".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0, type.toByte(), 0) +
            ByteArray(size) { 0x42.toByte() }

    private fun reader(data: ByteArray, visited: MutableSet<Long>? = null):
        (Long, ByteArray, Int, Int) -> Int = { pos, into, off, count ->
        visited?.add(pos / 65536L)
        if (pos >= data.size || pos < 0L) -1 else {
            val n = minOf(count, data.size - pos.toInt())
            System.arraycopy(data, pos.toInt(), into, off, n)
            n
        }
    }

    private fun picture(
        type: Int, image: ByteArray, mime: String = "image/png",
        description: String = "cover",
    ): ByteArray {
        val mimeData = mime.toByteArray(Charsets.US_ASCII)
        val descriptionData = description.toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        output.write(be32(type))
        output.write(be32(mimeData.size))
        output.write(mimeData)
        output.write(be32(descriptionData.size))
        output.write(descriptionData)
        for (number in intArrayOf(1200, 1200, 24, 0)) output.write(be32(number))
        output.write(be32(image.size))
        output.write(image)
        return output.toByteArray()
    }

    private fun flac(vararg pictures: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0, 0, 0, 34))
        out.write(ByteArray(34))
        pictures.forEachIndexed { index, data ->
            out.write(byteArrayOf(
                (if (index == pictures.lastIndex) 0x86 else 6).toByte(),
                (data.size ushr 16).toByte(),
                (data.size ushr 8).toByte(),
                data.size.toByte(),
            ))
            out.write(data)
        }
        return out.toByteArray()
    }

    @Test fun mp3V23ReturnsPreciseImageByteRangeWithoutDownloadingImage() {
        val pic = apic(size = 280_000)
        val file = mp3(3, frame("TIT2", byteArrayOf(0, 65), 3),
            frame("APIC", pic, 3))
        val visited = mutableSetOf<Long>()
        val result = EmbeddedArtworkLocator().locate(
            "song.mp3", file.size.toLong(), reader(file, visited))
        assertEquals(ArtworkLookupCode.FOUND, result.code)
        assertEquals("image/jpeg", result.artwork?.mimeType)
        assertEquals(3, result.artwork?.pictureType)
        assertEquals(280_000, result.artwork?.length)
        val at = result.artwork!!.offset.toInt()
        assertEquals(0x42.toByte(), file[at])
        assertEquals(0x42.toByte(), file[at + 279_999])
        assertTrue("Locator fetched too many distinct 64KiB regions", visited.size <= 2)
    }

    @Test fun mp3V24PrefersFrontCoverOverFirstBackCover() {
        val back = frame("APIC", apic(type = 4, size = 80_000), 4)
        val front = frame("APIC", apic(type = 3, size = 1024), 4)
        val file = mp3(4, back, front)
        val result = EmbeddedArtworkLocator().locate(
            "song.mp3", file.size.toLong(), reader(file))
        assertEquals(ArtworkLookupCode.FOUND, result.code)
        assertEquals(3, result.artwork?.pictureType)
        assertEquals(1024, result.artwork?.length)
    }

    @Test fun flacSkipsImageBytesAndPrefersFrontPicture() {
        val back = picture(4, ByteArray(250_000) { 0x33.toByte() })
        val front = picture(3, ByteArray(1000) { 0x77.toByte() })
        val data = flac(back, front)
        val visited = mutableSetOf<Long>()
        val result = EmbeddedArtworkLocator().locate(
            "song.flac", data.size.toLong(), reader(data, visited))
        assertEquals(ArtworkLookupCode.FOUND, result.code)
        assertEquals(3, result.artwork?.pictureType)
        assertEquals("image/png", result.artwork?.mimeType)
        assertEquals(1000, result.artwork?.length)
        assertEquals(0x77.toByte(), data[result.artwork!!.offset.toInt()])
        assertTrue(visited.size <= 3)
    }

    @Test fun flacUsesAvailableNonFrontCoverOnlyIfNoFrontExists() {
        val data = flac(picture(4, ByteArray(50)))
        val result = EmbeddedArtworkLocator().locate(
            "song.flac", data.size.toLong(), reader(data))
        assertEquals(ArtworkLookupCode.FOUND, result.code)
        assertEquals(4, result.artwork?.pictureType)
    }

    @Test fun noArtworkOrUnsupportedIsSafeAndDoesNotReadUnknownFormats() {
        val noArt = mp3(3, frame("TIT2", byteArrayOf(0, 65), 3))
        assertEquals(ArtworkLookupCode.NOT_FOUND,
            EmbeddedArtworkLocator().locate("a.mp3", noArt.size.toLong(),
                reader(noArt)).code)
        val unsupported = EmbeddedArtworkLocator().locate("b.ogg", 20000,
            { _, _, _, _ -> fail("Unknown format must not be read"); -1 })
        assertEquals(ArtworkLookupCode.UNSUPPORTED_FORMAT, unsupported.code)
    }

    @Test fun oversizedApicAndMalformedOffsetsFailClosed() {
        val big = mp3(3, frame("APIC", apic(size = 8 * 1024 * 1024 + 1), 3))
        val result = EmbeddedArtworkLocator().locate(
            "big.mp3", big.size.toLong(), reader(big))
        assertEquals(ArtworkLookupCode.NOT_FOUND, result.code)
        val broken = mp3(3, frame("APIC", apic(size = 42), 3)).copyOf()
        // Invalid synchsafe header (high bit must be clear).
        broken[6] = 0x80.toByte()
        assertEquals(ArtworkLookupCode.INVALID_METADATA,
            EmbeddedArtworkLocator().locate("bad.mp3", broken.size.toLong(),
                reader(broken)).code)
    }

    @Test fun malformedFlacPictureLengthAndReadFailuresStaySafe() {
        val block = picture(3, ByteArray(120))
        val data = flac(block).copyOf()
        // Claim an image 1 byte larger than the enclosing picture block.
        val imageLengthField = data.size - 120 - 4
        data[imageLengthField + 3] = 121.toByte()
        assertEquals(ArtworkLookupCode.NOT_FOUND,
            EmbeddedArtworkLocator().locate("bad.flac", data.size.toLong(),
                reader(data)).code)
        assertEquals(ArtworkLookupCode.READ_FAILED,
            EmbeddedArtworkLocator().locate("error.flac", data.size.toLong(),
                { _, _, _, _ -> throw java.io.IOException("private path") }).code)
    }
}
