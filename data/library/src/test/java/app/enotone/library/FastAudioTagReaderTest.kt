package app.enotone.library

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class FastAudioTagReaderTest {
    private fun byteReader(bytes: ByteArray, blocks: MutableSet<Long>? = null):
        (Long, ByteArray, Int, Int) -> Int = { pos, out, offset, len ->
        blocks?.add(pos / 65536L)
        if (pos < 0 || pos >= bytes.size) -1 else {
            val n = minOf(len, bytes.size - pos.toInt())
            System.arraycopy(bytes, pos.toInt(), out, offset, n)
            n
        }
    }

    private fun int32(v: Int) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
    )

    private fun sync32(v: Int) = byteArrayOf(
        ((v ushr 21) and 0x7F).toByte(), ((v ushr 14) and 0x7F).toByte(),
        ((v ushr 7) and 0x7F).toByte(), (v and 0x7F).toByte(),
    )

    private fun little32(v: Int) = byteArrayOf(
        v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte(),
    )

    private fun putInt32(bytes: ByteArray, at: Int, value: Int) {
        val encoded = int32(value)
        System.arraycopy(encoded, 0, bytes, at, encoded.size)
    }

    private fun putUnsignedLong(bytes: ByteArray, at: Int, value: Long) {
        for (index in 0 until 8) {
            bytes[at + index] = (value ushr (56 - index * 8)).toByte()
        }
    }

    private fun frame(id: String, data: ByteArray, version: Int): ByteArray =
        id.toByteArray(StandardCharsets.US_ASCII) +
            (if (version == 4) sync32(data.size) else int32(data.size)) +
            byteArrayOf(0, 0) + data

    private fun textFrame(id: String, text: String, version: Int): ByteArray {
        val bytes = if (version == 4) {
            byteArrayOf(3) + text.toByteArray(Charsets.UTF_8)
        } else {
            byteArrayOf(1) + text.toByteArray(Charsets.UTF_16)
        }
        return frame(id, bytes, version)
    }

    private fun mp3(version: Int, vararg frames: ByteArray): ByteArray {
        val body = frames.fold(ByteArray(0)) { a, b -> a + b }
        return "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, 0) +
            sync32(body.size) + body + ByteArray(4096)
    }

    private fun vorbis(vararg fields: String): ByteArray {
        val vendor = "synthetic-test".toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(little32(vendor.size))
        out.write(vendor)
        out.write(little32(fields.size))
        fields.forEach { field ->
            val text = field.toByteArray(Charsets.UTF_8)
            out.write(little32(text.size))
            out.write(text)
        }
        return out.toByteArray()
    }

    private fun flacWithHugeCover(comments: ByteArray): ByteArray {
        val coverSize = 300_000
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0, 0, 0, 34)) // STREAMINFO
        out.write(ByteArray(34))
        out.write(byteArrayOf(6, (coverSize ushr 16).toByte(),
            (coverSize ushr 8).toByte(), coverSize.toByte())) // PICTURE
        out.write(ByteArray(coverSize) { 0x3A.toByte() })
        out.write(byteArrayOf(0x84.toByte(), (comments.size ushr 16).toByte(),
            (comments.size ushr 8).toByte(), comments.size.toByte())) // last VORBIS
        out.write(comments)
        out.write(ByteArray(4096))
        return out.toByteArray()
    }

    @Test fun mp3V23ReadsUnicodeAndSkipsLargeApicWithoutDownloadingIt() {
        val bigCover = frame("APIC", ByteArray(280_000) { 0x42.toByte() }, 3)
        val data = mp3(3, bigCover,
            textFrame("TIT2", "Ёж", 3),
            textFrame("TPE1", "Исполнитель", 3),
            textFrame("TALB", "Альбом", 3),
            textFrame("TPE2", "Группа", 3))
        val visited = mutableSetOf<Long>()
        val r = FastAudioTagReader().read("Track.mp3", data.size.toLong(),
            byteReader(data, visited))
        assertEquals(FastTagCode.MP3_ID3V23, r.code)
        assertEquals("Ёж", r.tags?.title)
        assertEquals("Исполнитель", r.tags?.artist)
        assertEquals("Альбом", r.tags?.album)
        assertEquals("Группа", r.tags?.albumArtist)
        assertTrue(r.embeddedTitle)
        assertTrue(visited.size <= 2)
        assertTrue(visited.contains(0L))
    }

    @Test fun mp3V24SupportsSynchsafeFrameSizesAndUtf8() {
        val data = mp3(4,
            textFrame("TIT2", "Title", 4),
            textFrame("TPE1", "Artist", 4),
            textFrame("TALB", "Album", 4),
            textFrame("TPE2", "Album Artist", 4))
        val r = FastAudioTagReader().read("s.mp3", data.size.toLong(), byteReader(data))
        assertEquals(FastTagCode.MP3_ID3V24, r.code)
        assertEquals("Title", r.tags?.title)
        assertEquals("Artist", r.tags?.artist)
        assertEquals("Album Artist", r.tags?.albumArtist)
    }

    @Test fun mp3V23AndV24ReadYearTrackAndDiscEvenAfterArtwork() {
        for (version in listOf(3, 4)) {
            val data = mp3(version,
                frame("APIC", ByteArray(96_000), version),
                textFrame("TIT2", "Song", version),
                textFrame("TALB", "Album", version),
                textFrame(if (version == 3) "TYER" else "TDRC",
                    if (version == 3) "1998" else "2021-12-31", version),
                textFrame("TRCK", "07/13", version),
                textFrame("TPOS", "2/2", version),
            )
            val visited = mutableSetOf<Long>()
            val result = FastAudioTagReader().read(
                "song.mp3", data.size.toLong(), byteReader(data, visited))
            assertEquals(if (version == 3) FastTagCode.MP3_ID3V23
                else FastTagCode.MP3_ID3V24, result.code)
            assertEquals(if (version == 3) 1998 else 2021, result.tags?.year)
            assertEquals(7, result.tags?.trackNumber)
            assertEquals(2, result.tags?.discNumber)
            assertTrue(visited.size <= 2)
        }
    }

    @Test fun flacReadsDateAndPositionWithoutFetchingPicturePayload() {
        val data = flacWithHugeCover(vorbis(
            "TITLE=Song", "ALBUM=Disc set", "DATE=2019",
            "TRACKNUMBER=9/11", "DISCNUMBER=2/2"))
        val visited = mutableSetOf<Long>()
        val result = FastAudioTagReader().read(
            "album.flac", data.size.toLong(), byteReader(data, visited))
        assertEquals(FastTagCode.FLAC_VORBIS, result.code)
        assertEquals(2019, result.tags?.year)
        assertEquals(9, result.tags?.trackNumber)
        assertEquals(2, result.tags?.discNumber)
        assertEquals(2, visited.size)
    }

    @Test fun id3V23AndV24ReadGenreFromSameFastMetadataPass() {
        for (version in listOf(3, 4)) {
            val bytes = mp3(version,
                frame("APIC", ByteArray(96_000), version),
                textFrame("TIT2", "Track", version),
                textFrame("TCON", if (version == 3) "(17)" else "Electronic", version),
            )
            val blocks = mutableSetOf<Long>()
            val read = FastAudioTagReader().read(
                "test.mp3", bytes.size.toLong(), byteReader(bytes, blocks))
            assertEquals(if (version == 3) "Rock" else "Electronic", read.tags?.genre)
            assertEquals("Track", read.tags?.title)
            assertTrue(blocks.size <= 2)
        }
    }

    @Test fun flacGenreIsReadPastLargeArtworkWithOtherTags() {
        val bytes = flacWithHugeCover(vorbis(
            "TITLE=Мелодия", "ALBUM=Альбом", "GENRE=Русский рок",
            "YEAR=2021", "TRACKNUMBER=2"))
        val blocks = mutableSetOf<Long>()
        val read = FastAudioTagReader().read(
            "song.flac", bytes.size.toLong(), byteReader(bytes, blocks))
        assertEquals("Русский рок", read.tags?.genre)
        assertEquals(2021, read.tags?.year)
        assertEquals(2, read.tags?.trackNumber)
        assertEquals(2, blocks.size)
    }

    @Test fun id3V1GenreByteIsReadWithTheSameTailRequest() {
        val tag = ByteArray(128)
        System.arraycopy("TAG".toByteArray(), 0, tag, 0, 3)
        System.arraycopy("Song".toByteArray(), 0, tag, 3, 4)
        tag[127] = 17 // Standard ID3v1 Rock, not a tag string.
        val bytes = ByteArray(200_000) + tag
        val blocks = mutableSetOf<Long>()
        val read = FastAudioTagReader().read(
            "old.mp3", bytes.size.toLong(), byteReader(bytes, blocks))
        assertEquals("Rock", read.tags?.genre)
        assertTrue(blocks.size <= 2)
    }

    @Test fun genreAloneCountsAsRealEmbeddedTag() {
        val bytes = mp3(4, textFrame("TCON", "Ambient", 4))
        val read = FastAudioTagReader().read("genre.mp3", bytes.size.toLong(), byteReader(bytes))
        assertEquals(FastTagCode.MP3_ID3V24, read.code)
        assertEquals("Ambient", read.tags?.genre)
        assertFalse(read.embeddedTitle)
    }

    @Test fun mp3WithoutV2UsesId3v1FromTailOnly() {
        val prefix = ByteArray(200_000) { 0x33.toByte() }
        val tag = ByteArray(128)
        System.arraycopy("TAG".toByteArray(), 0, tag, 0, 3)
        System.arraycopy("Old Title".toByteArray(), 0, tag, 3, 9)
        System.arraycopy("Old Artist".toByteArray(), 0, tag, 33, 10)
        val file = prefix + tag
        val visited = mutableSetOf<Long>()
        val result = FastAudioTagReader().read("song.mp3", file.size.toLong(),
            byteReader(file, visited))
        assertEquals(FastTagCode.MP3_ID3V1, result.code)
        assertEquals("Old Title", result.tags?.title)
        assertEquals("Old Artist", result.tags?.artist)
        assertTrue(visited.size <= 2)
    }

    @Test fun flacSkipsHugePictureAndReadsUtf8VorbisComments() {
        val data = flacWithHugeCover(vorbis(
            "TITLE=Песня", "artist=Исполнитель", "ALBUM=Альбом",
            "ALBUMARTIST=Сборник", "unrelated=X"))
        val visited = mutableSetOf<Long>()
        val result = FastAudioTagReader().read("piece.flac", data.size.toLong(),
            byteReader(data, visited))
        assertEquals(FastTagCode.FLAC_VORBIS, result.code)
        assertEquals("Песня", result.tags?.title)
        assertEquals("Исполнитель", result.tags?.artist)
        assertEquals("Альбом", result.tags?.album)
        assertEquals("Сборник", result.tags?.albumArtist)
        assertEquals(2, visited.size)
    }

    @Test fun flacAlbumArtistAliasesAreStoredSeparatelyFromTrackArtist() {
        for (alias in listOf("ALBUMARTIST", "ALBUM ARTIST", "ALBUM_ARTIST")) {
            val data = flacWithHugeCover(vorbis(
                "ARTIST=Track Guest", "ALBUM=Collaborations",
                "$alias=Album Collective"))
            val result = FastAudioTagReader().read(
                "collab.flac", data.size.toLong(), byteReader(data))
            assertEquals(FastTagCode.FLAC_VORBIS, result.code)
            assertEquals("Track Guest", result.tags?.artist)
            assertEquals("Album Collective", result.tags?.albumArtist)
        }
    }

    @Test fun flacMalformedCommentsAndOversizedMetadataFailClosed() {
        val valid = vorbis("TITLE=Safe")
        val data = flacWithHugeCover(valid)
        val mutated = data.copyOf()
        val startOfComment = mutated.size - 4096 - valid.size
        for (i in 0..3) mutated[startOfComment + i] = 0x7f
        assertEquals(FastTagCode.INVALID_METADATA,
            FastAudioTagReader().read("x.flac", mutated.size.toLong(),
                byteReader(mutated)).code)
        val huge = flacWithHugeCover(ByteArray(70_000))
        assertEquals(FastTagCode.FAST_BUDGET_EXCEEDED,
            FastAudioTagReader().read("x.flac", huge.size.toLong(),
                byteReader(huge)).code)
    }

    @Test fun unsupportedLayoutsNeverUseExpensivePlatformFallback() {
        val unsupported = mp3(3, textFrame("TIT2", "Safe", 3)).copyOf()
        unsupported[5] = 0x80.toByte() // unsynchronisation not accepted yet
        var reads = 0
        val r = FastAudioTagReader().read("song.mp3", unsupported.size.toLong(),
            { pos, buffer, offset, size ->
                reads++
                byteReader(unsupported)(pos, buffer, offset, size)
            })
        assertEquals(FastTagCode.UNSUPPORTED_LAYOUT, r.code)
        assertEquals(1, reads)
        assertEquals(FastTagCode.UNSUPPORTED_FORMAT,
            FastAudioTagReader().read("song.ogg", 4096,
                { _, _, _, _ -> fail("Should not read unsupported format"); -1 }).code)
    }

    @Test fun flacDurationComesFromCachedStreamInfoBytes() {
        val data = ByteArray(42)
        System.arraycopy("fLaC".toByteArray(), 0, data, 0, 4)
        data[4] = 0x80.toByte()
        data[7] = 34
        val samples = 44_100L * 60L
        putUnsignedLong(data, 18, (44_100L shl 44) or samples)
        val cacheRead = byteReader(data)
        val requests = mutableListOf<Long>()
        val read = FastAudioTagReader().read(
            "minute.flac", data.size.toLong(),
            { position, output, offset, length ->
                requests += position
                byteReader(data)(position, output, offset, length)
            },
            cacheRead,
        )
        assertEquals(60_000L, read.durationMs)
        assertEquals(FastTagCode.NO_EMBEDDED_TAGS, read.code)
        assertTrue(requests.all { it < 65_536L })
    }

    @Test fun mp3DurationUsesXingFrameCountWithoutAnotherRangeRead() {
        val data = ByteArray(4096)
        data[0] = 0xFF.toByte()
        data[1] = 0xFB.toByte() // MPEG-1 Layer III, no CRC
        data[2] = 0x90.toByte() // 128 kbps, 44.1 kHz
        data[3] = 0x64
        System.arraycopy("Xing".toByteArray(), 0, data, 36, 4)
        putInt32(data, 40, 1)
        putInt32(data, 44, 1000)
        var requestsWithDuration = 0
        val cacheRead = byteReader(data)
        val result = FastAudioTagReader().read(
            "xing.mp3", data.size.toLong(),
            { position, output, offset, length ->
                requestsWithDuration++
                byteReader(data)(position, output, offset, length)
            },
            cacheRead,
        )
        var requestsWithoutDuration = 0
        FastAudioTagReader().read(
            "xing.mp3", data.size.toLong(),
            { position, output, offset, length ->
                requestsWithoutDuration++
                byteReader(data)(position, output, offset, length)
            },
        )
        assertEquals(26_122L, result.durationMs)
        assertEquals(requestsWithoutDuration, requestsWithDuration)
    }

    @Test fun mp3DurationUsesVbriFrameCount() {
        val data = ByteArray(4096)
        data[0] = 0xFF.toByte()
        data[1] = 0xFB.toByte()
        data[2] = 0x90.toByte()
        data[3] = 0x64
        System.arraycopy("VBRI".toByteArray(), 0, data, 36, 4)
        putInt32(data, 50, 2000)
        val result = FastAudioTagReader().read(
            "vbri.mp3", data.size.toLong(), byteReader(data), byteReader(data))
        assertEquals(52_244L, result.durationMs)
    }

    @Test fun mp3WithoutXingOrVbriHasUnknownDuration() {
        val data = ByteArray(4096)
        val result = FastAudioTagReader().read(
            "cbr.mp3", data.size.toLong(), byteReader(data), byteReader(data))
        assertNull(result.durationMs)
    }

    @Test fun damagedDurationHeadersReturnNullWithoutThrowing() {
        val damagedFlac = ByteArray(42)
        System.arraycopy("fLaC".toByteArray(), 0, damagedFlac, 0, 4)
        damagedFlac[4] = 0x80.toByte()
        damagedFlac[7] = 34
        val damaged = FastAudioTagReader().read(
            "damaged.flac", damagedFlac.size.toLong(),
            byteReader(damagedFlac), byteReader(damagedFlac))
        assertNull(damaged.durationMs)
        assertEquals(FastTagCode.NO_EMBEDDED_TAGS, damaged.code)

        val truncated = FastAudioTagReader().read(
            "short.flac", 12, byteReader(damagedFlac), byteReader(damagedFlac))
        assertNull(truncated.durationMs)
        assertEquals(FastTagCode.INVALID_METADATA, truncated.code)
    }

    @Test fun mockWebDavProvesTwoGetForFlacMetadataBehindLargeCover() {
        val fixture = flacWithHugeCover(vorbis("TITLE=Demo", "ARTIST=Example"))
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(req: RecordedRequest): MockResponse {
                val value = req.getHeader("Range") ?: return MockResponse().setResponseCode(400)
                val m = Regex("""bytes=(\d+)-(\d+)""").matchEntire(value)
                    ?: return MockResponse().setResponseCode(400)
                val start = m.groupValues[1].toInt()
                val end = m.groupValues[2].toInt()
                return MockResponse().setResponseCode(206)
                    .addHeader("Content-Range", "bytes=$start-$end/" + fixture.size)
                    .addHeader("ETag", "\"v1\"")
                    .setBody(Buffer().write(fixture, start, end - start + 1))
            }
        }
        server.start()
        try {
            WebDavRangeReader(
                libraryRoot = server.url("/AllMusic/").toString(),
                encodedPath = "/AllMusic/demo.flac",
                fileSize = fixture.size.toLong(),
                email = "synthetic@example.test", appPassword = "only-for-mock",
                etag = "\"v1\"", allowLoopbackHttpForTests = true,
            ).use { source ->
                val r = FastAudioTagReader().read(
                    "demo.flac", source.fileSize, source::readAt, source::readCachedAt)
                assertEquals(FastTagCode.FLAC_VORBIS, r.code)
                assertEquals("Demo", r.tags?.title)
                assertEquals(2, source.stats().requests)
                assertEquals(65536L + (fixture.size - 262144L), source.stats().downloadedBytes)
            }
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test fun mockWebDavReadsMp3XingDurationWithoutAnotherGet() {
        val fixture = ByteArray(4096)
        fixture[0] = 0xFF.toByte()
        fixture[1] = 0xFB.toByte()
        fixture[2] = 0x90.toByte()
        fixture[3] = 0x64
        System.arraycopy("Xing".toByteArray(), 0, fixture, 36, 4)
        putInt32(fixture, 40, 1)
        putInt32(fixture, 44, 1000)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(req: RecordedRequest): MockResponse {
                val range = req.getHeader("Range") ?: return MockResponse().setResponseCode(400)
                val bounds = Regex("""bytes=(\d+)-(\d+)""").matchEntire(range)
                    ?: return MockResponse().setResponseCode(400)
                val start = bounds.groupValues[1].toInt()
                val end = bounds.groupValues[2].toInt()
                return MockResponse().setResponseCode(206)
                    .addHeader("Content-Range", "bytes=$start-$end/${fixture.size}")
                    .addHeader("ETag", "\"v1\"")
                    .setBody(Buffer().write(fixture, start, end - start + 1))
            }
        }
        server.start()
        try {
            WebDavRangeReader(
                libraryRoot = server.url("/AllMusic/").toString(),
                encodedPath = "/AllMusic/xing.mp3",
                fileSize = fixture.size.toLong(),
                email = "synthetic@example.test", appPassword = "only-for-mock",
                etag = "\"v1\"", allowLoopbackHttpForTests = true,
            ).use { source ->
                val read = FastAudioTagReader().read(
                    "xing.mp3", source.fileSize, source::readAt, source::readCachedAt)
                assertEquals(26_122L, read.durationMs)
                assertEquals(1, source.stats().requests)
            }
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
