package app.enotone.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Byte-accurate container fixtures from `tools/generate_tag_fixtures.py`.
 *
 * These fixtures encode the *correct* real-world layouts that taggers emit, so
 * every assertion below states the semantically correct outcome. They are the
 * proof of the R-A7 defect: the fast parser refuses these layouts and reports
 * them as a code that the indexing pipeline treats as a successful empty check.
 * Do not weaken an assertion to match the current parser — the fix belongs in a
 * separate commit (see AGENTS.md §4: existing/new assertions may not be relaxed
 * inside a fix PR without justification).
 */
class TagReaderFixtureTest {
    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/tags/$name")) {
            "Missing fixture $name; run tools/generate_tag_fixtures.py"
        }.use { it.readBytes() }

    private fun reader(bytes: ByteArray): (Long, ByteArray, Int, Int) -> Int =
        { position, output, offset, length ->
            if (position < 0 || position >= bytes.size) -1 else {
                val count = minOf(length, bytes.size - position.toInt())
                System.arraycopy(bytes, position.toInt(), output, offset, count)
                count
            }
        }

    private fun read(name: String, useCache: Boolean = true): FastTagRead {
        val bytes = fixture(name)
        val source = reader(bytes)
        return FastAudioTagReader().read(
            fileName = name,
            fileSize = bytes.size.toLong(),
            readAt = source,
            readCachedAt = if (useCache) source else null,
        )
    }

    /**
     * Same as [read], but lets a test poke one byte so a *valid* fixture can be
     * turned into the layout the parser is expected to refuse. Needed because
     * `id3v23_ext_header.mp3` became readable when the extended-header defect
     * was fixed, so it can no longer play the "refused" role by itself.
     */
    private fun readMutated(name: String, mutate: (ByteArray) -> Unit): FastTagRead {
        val bytes = fixture(name)
        mutate(bytes)
        val source = reader(bytes)
        return FastAudioTagReader().read(
            fileName = name,
            fileSize = bytes.size.toLong(),
            readAt = source,
            readCachedAt = source,
        )
    }

    /** ID3v2.3 with the extended-header flag (0x40) and a valid 10-byte ext header. */
    @Test fun id3v23ExtendedHeaderIsSkippedInsteadOfRejectingTheWholeTag() {
        val read = read("id3v23_ext_header.mp3")
        assertEquals(FastTagCode.MP3_ID3V23, read.code)
        assertEquals("Extended Header", read.tags?.title)
        assertEquals("Header Ensemble", read.tags?.artist)
    }

    /**
     * ID3v2.4 tag header flag 0x80 (unsynchronisation). Per id3.org/id3v2.4.0
     * structure 3.3 a frame size is "the size of the data in the final frame,
     * after ... unsynchronisation", so the fixture stores each frame's escaped
     * bytes on disk and declares that stored length: TIT2 declares 33 bytes for
     * 31 decoded ones, TPE1 declares 25 for 23.
     *
     * Both frames are UTF-16 LE with BOM `FF FE`, so each body contains two
     * sequences that must be escaped (`FF FE` -> `FF 00 FE`, `FF 00` ->
     * `FF 00 00`). The second frame therefore starts at a stored offset that a
     * reader which de-unsynchronises the *whole tag body* and then trusts the
     * stored frame sizes can no longer reach: its walk lands two bytes into
     * TPE1's header and the artist is lost with [FastTagCode.INVALID_METADATA].
     *
     * That is defect R-1: the size is post-unsync, the walk must be post-unsync,
     * and each frame has to be de-unsynchronised on its own.
     */
    @Test fun id3v24UnsynchronisedFramesAreDecodedAfterDeUnsynchronisation() {
        val read = read("id3v24_unsync.mp3")
        assertEquals(
            "TIT2 (first frame) must keep its text; actual title=${read.tags?.title}, " +
                "artist=${read.tags?.artist}, code=${read.code}",
            "Unsync \u00ff Title", read.tags?.title,
        )
        assertEquals(
            "TPE1 is the second frame and must survive the stored (post-unsync) " +
                "frame size; actual artist=${read.tags?.artist}, " +
                "title=${read.tags?.title}, code=${read.code}",
            "Ансамбль \u00ff", read.tags?.artist,
        )
        assertEquals(FastTagCode.MP3_ID3V24, read.code)
    }

    /**
     * Taggers prepend an ID3v2 block to FLAC; the fLaC marker then starts later.
     *
     * R-5: the fixture also carries a literal `fLaC` inside a TXXX frame, so the
     * offset must come from the ID3 header size (10 + synchsafe size), not from
     * the first occurrence of the marker in the first 64 KiB.
     */
    @Test fun flacWithId3PreambleStillExposesVorbisComments() {
        val read = read("flac_id3_preamble.flac")
        assertEquals(FastTagCode.FLAC_VORBIS, read.code)
        assertEquals("Prelude", read.tags?.title)
        assertEquals("Ensemble", read.tags?.artist)
    }

    /**
     * R-5 reserve path: a zeroed ID3 size field makes the computed offset point
     * at nothing, so the bounded window search has to recover the real marker.
     */
    @Test fun flacWithBrokenId3SizeStillFindsTheMarkerBySearch() {
        val read = read("flac_id3_bad_size.flac")
        assertEquals(FastTagCode.FLAC_VORBIS, read.code)
        assertEquals("Broken Size", read.tags?.title)
        assertEquals("Recovered", read.tags?.artist)
    }

    /** Encoding byte 0 must not turn CP1251 Cyrillic into ISO-8859-1 mojibake. */
    @Test fun cp1251BytesDeclaredAsEncodingZeroAreDecodedAsCyrillic() {
        val read = read("cp1251_tags.mp3")
        assertEquals(FastTagCode.MP3_ID3V23, read.code)
        assertEquals("Сердце", read.tags?.title)
        assertEquals("Кино", read.tags?.artist)
    }

    /**
     * R-4: detection must survive the shapes real taggers produce — typographic
     * quotes (`« »`), dashes (`– —`), Ukrainian letters (`і ї є ґ`) and Latin
     * words standing next to Cyrillic ones. The old rule switched on only when
     * the payload had *no* ASCII letters, so any Latin word (a featured artist,
     * "Live", "Part 2") silently returned ISO-8859-1 mojibake.
     */
    @Test fun cp1251DetectionKeepsMixedQuotesDashesAndLatinWords() {
        val read = read("cp1251_mixed_tags.mp3")
        assertEquals(
            "Mixed-script title must stay CP1251; actual title=${read.tags?.title}",
            "«Осень» — Live (feat. Ivan)", read.tags?.title,
        )
        assertEquals(
            "Ukrainian letters must decode; actual artist=${read.tags?.artist}",
            "Їжак Ґанок і Хор", read.tags?.artist,
        )
        assertEquals("Дорога – Part 2", read.tags?.album)
        assertEquals(FastTagCode.MP3_ID3V23, read.code)
    }

    /**
     * R-4 guard in the opposite direction: genuine ISO-8859-1 with accented
     * letters must NOT be reinterpreted as CP1251. Read as Windows-1251 the
     * bytes glue Cyrillic letters to Latin ones inside one word (`Cafй`,
     * `Mьller`, `GrьЯe`), which is impossible in real Cyrillic text.
     */
    @Test fun latin1AccentedTextIsNotMistakenForCp1251() {
        val read = read("latin1_accented_tags.mp3")
        assertEquals("Café Müller", read.tags?.title)
        assertEquals("Grüße", read.tags?.artist)
        assertEquals(FastTagCode.MP3_ID3V23, read.code)
    }

    /** A broken STREAMINFO must not cost the listener otherwise readable tags. */
    @Test fun corruptStreamInfoDoesNotDiscardFollowingVorbisComments() {
        val read = read("flac_corrupt_streaminfo.flac")
        assertEquals(FastTagCode.FLAC_VORBIS, read.code)
        assertEquals("Salvage", read.tags?.title)
        assertEquals("Recovery", read.tags?.artist)
    }

    /**
     * CBR without Xing/Info/VBRI still has a frame count a reader can compute.
     *
     * This fixture is the positive side of R-6: all 100 frames declare the same
     * bitrate, version and sample rate, so verifying the following headers must
     * keep the derived duration instead of turning every CBR file into "unknown".
     */
    @Test fun constantBitrateMp3DerivesDurationFromFrameHeaders() {
        val read = read("mp3_cbr_no_xing.mp3")
        assertEquals(FastTagCode.MP3_ID3V23, read.code)
        assertEquals("Constant Rate", read.tags?.title)
        assertNotNull("CBR MP3 without Xing/Info/VBRI must not report unknown duration",
            read.durationMs)
        assertTrue("100 frames of 1152 samples at 44.1 kHz are about 2612 ms",
            read.durationMs!! in 2_500L..2_700L)
    }

    /**
     * Defect (R-6): the first frame header alone cannot distinguish CBR from VBR.
     * Here frames alternate between 128 and 192 kbps and nothing declares a frame
     * count, so dividing the file size by the first frame's 417 bytes yields a
     * plausible but wrong duration. Verifying the following headers must make the
     * reader say "unknown" instead of inventing a number.
     *
     * The tags are still readable: only the duration verdict changes.
     */
    @Test fun variableBitrateWithoutFrameCountReportsUnknownDuration() {
        val read = read("mp3_vbr_no_xing.mp3")
        assertEquals(FastTagCode.MP3_ID3V23, read.code)
        assertEquals("Variable Rate", read.tags?.title)
        assertEquals("Sine Worker", read.tags?.artist)
        assertNull("Frames disagree on bitrate: no frame count can be derived",
            read.durationMs)
    }

    /**
     * The byte-level parser *does* distinguish "nothing to read" from "refused to
     * read". This guard keeps that distinction from silently disappearing.
     */
    @Test fun parserDistinguishesAbsentTagsFromRefusedLayout() {
        val absent = read("mp3_no_tags.mp3")
        assertEquals(FastTagCode.NO_EMBEDDED_TAGS, absent.code)
        assertNull(absent.tags)

        // Tag-level unsynchronisation on an ID3v2.3 tag is still refused: v2.3
        // stores whole-tag sizes before unsync, so the byte count cannot be
        // trusted. Flip the extended-header flag to the unsync flag.
        val refused = readMutated("id3v23_ext_header.mp3") { it[5] = 0x80.toByte() }
        assertNull(refused.tags)
        // Property, not a pinned enum value: the exact refusal code may change
        // with the fix, but "refused" must never look like "nothing was there".
        assertNotEquals(FastTagCode.NO_EMBEDDED_TAGS, refused.code)
    }

    /**
     * Defect (R-A7): the parsing layer knows the tag exists and was refused, but
     * [PilotFailures.fromParserResult] used to map every parse refusal except
     * INVALID_METADATA / FAST_BUDGET_EXCEEDED to `null`, so the indexing record
     * looked like a completed check with zero tags. The user saw a track with no
     * title and no retry, exactly as if the file never had tags.
     *
     * Fixed by the least invasive option: [FastTagCode.UNSUPPORTED_LAYOUT] now
     * yields an explicit `FAILED_LAYOUT` failure, so `FastTagRead` keeps its
     * shape and callers stop treating a verdict as an empty result. A non-MP3
     * or non-FLAC extension ([FastTagCode.UNSUPPORTED_FORMAT]) is deliberately
     * not a layout verdict and stays a completed check without tags (R-2).
     */
    @Test fun refusedLayoutIsNotRecordedAsACompletedEmptyCheck() {
        assertNotNull("A refused tag layout must be reported as a parser failure",
            PilotFailures.fromParserResult(FastTagCode.UNSUPPORTED_LAYOUT, null))
    }
}
