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

    /** ID3v2.4 with unsynchronisation (0x80); payload decodes after de-unsync. */
    @Test fun id3v24UnsynchronisedFramesAreDecodedAfterDeUnsynchronisation() {
        val read = read("id3v24_unsync.mp3")
        assertEquals(FastTagCode.MP3_ID3V24, read.code)
        assertEquals("Unsync \u00ff Title", read.tags?.title)
        assertEquals("Unsync Artist", read.tags?.artist)
    }

    /** Taggers prepend an ID3v2 block to FLAC; the fLaC marker then starts later. */
    @Test fun flacWithId3PreambleStillExposesVorbisComments() {
        val read = read("flac_id3_preamble.flac")
        assertEquals(FastTagCode.FLAC_VORBIS, read.code)
        assertEquals("Prelude", read.tags?.title)
        assertEquals("Ensemble", read.tags?.artist)
    }

    /** Encoding byte 0 must not turn CP1251 Cyrillic into ISO-8859-1 mojibake. */
    @Test fun cp1251BytesDeclaredAsEncodingZeroAreDecodedAsCyrillic() {
        val read = read("cp1251_tags.mp3")
        assertEquals(FastTagCode.MP3_ID3V23, read.code)
        assertEquals("Сердце", read.tags?.title)
        assertEquals("Кино", read.tags?.artist)
    }

    /** A broken STREAMINFO must not cost the listener otherwise readable tags. */
    @Test fun corruptStreamInfoDoesNotDiscardFollowingVorbisComments() {
        val read = read("flac_corrupt_streaminfo.flac")
        assertEquals(FastTagCode.FLAC_VORBIS, read.code)
        assertEquals("Salvage", read.tags?.title)
        assertEquals("Recovery", read.tags?.artist)
    }

    /** CBR without Xing/Info/VBRI still has a frame count a reader can compute. */
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
     * Fixed by the least invasive option: [FastTagCode.UNSUPPORTED_LAYOUT] and
     * [FastTagCode.UNSUPPORTED_FORMAT] now yield an explicit `FAILED_LAYOUT`
     * failure, so `FastTagRead` keeps its shape and callers stop treating a
     * verdict as an empty result.
     */
    @Test fun refusedLayoutIsNotRecordedAsACompletedEmptyCheck() {
        assertNotNull("A refused tag layout must be reported as a parser failure",
            PilotFailures.fromParserResult(FastTagCode.UNSUPPORTED_LAYOUT, null))
    }
}
