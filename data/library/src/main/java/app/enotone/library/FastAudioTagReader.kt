package app.enotone.library

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.Locale

private const val MILLIS_PER_SECOND = 1_000L

// ID3v2 tag header, frame header and flag bits.
private const val ID3_HEADER_BYTES = 10
private const val ID3_FRAME_HEADER_BYTES = 10
private const val ID3_HEADER_FLAG_MASK = 0xF0
private const val ID3_FLAG_UNSYNC = 0x80
private const val ID3_FLAG_FOOTER = 0x10
private const val ID3_FLAG_EXTENDED_HEADER = 0x40
private const val ID3_EXTENDED_HEADER_BYTES = 4
private const val ID3_EXTENDED_HEADER_MIN = 6
private const val ID3_MAX_FRAMES = 256
private const val ID3_MAX_TEXT_FRAME_BYTES = 8192L
private const val ID3_FRAME_V23_GROUPING = 0x20
private const val ID3_FRAME_V23_COMPRESSION = 0x80
private const val ID3_FRAME_V23_ENCRYPTION = 0x40
private const val ID3_FRAME_GROUPING = 0x40
private const val ID3_FRAME_COMPRESSION = 0x08
private const val ID3_FRAME_ENCRYPTION = 0x04
private const val ID3_FRAME_UNSYNC = 0x02
private const val ID3_FRAME_DATA_LENGTH = 0x01
private const val ID3_DATA_LENGTH_BYTES = 4

// FLAC metadata block chain.
private const val FLAC_MARKER_BYTES = 4
private const val FLAC_BLOCK_HEADER_BYTES = 4
private const val FLAC_STREAMINFO_BYTES = 34
private const val FLAC_MAX_BLOCKS = 128
private const val FLAC_MAX_COMMENT_BYTES = 65536
private const val FLAC_PROBE_BYTES = 65536
private const val FLAC_LAST_BLOCK = 0x80
private const val FLAC_TYPE_MASK = 0x7F
private const val FLAC_TYPE_STREAMINFO = 0
private const val FLAC_TYPE_VORBIS = 4
private const val FLAC_TYPE_INVALID = 127
private const val FLAC_MARKER = "fLaC"

// MPEG-1/2 layer III frame geometry for constant-bitrate duration.
private const val MPEG1_LAYER3_COEFFICIENT = 144
private const val MPEG2_LAYER3_COEFFICIENT = 72

// Header fields that must repeat for a stream to count as constant bitrate:
// version, layer, sample-rate index and bitrate index. The padding bit and the
// private/copyright bits are deliberately excluded.
private const val MPEG_STREAM_MASK =
    (0x3 shl 19) or (0x3 shl 17) or (0xF shl 12) or (0x3 shl 10)

// How many frames after the first one must repeat its bitrate and sample rate
// before a stream without Xing/Info/VBRI may be called constant-bitrate.
private const val CBR_VERIFY_FRAMES = 2

// Windows-1251 detection for text frames that declare ISO-8859-1.
private const val CP1251_HIGH_BYTE_START = 0x80
private const val CP1251_MIN_CYRILLIC_LETTERS = 4
private val cyrillicLetters = '\u0400'..'\u04FF'

private val flacContinuationTypes = 1..6
private val wantedTextFrames = setOf(
    "TIT2", "TPE1", "TALB", "TPE2", "TDRC", "TYER", "TRCK", "TPOS", "TCON",
)
private val wantedVorbisTextFields = setOf(
    "TITLE", "ARTIST", "ALBUM", "DATE", "YEAR", "TRACKNUMBER", "DISCNUMBER", "GENRE",
)
private val mpeg1Layer3Kbps = intArrayOf(
    0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0,
)
private val mpeg2Layer3Kbps = intArrayOf(
    0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0,
)
private val windows1251: Charset by lazy {
    runCatching { Charset.forName("windows-1251") }.getOrDefault(Charsets.ISO_8859_1)
}

/** Fixed diagnostic codes: safe to display, never include titles or remote paths. */
enum class FastTagCode {
    MP3_ID3V23, MP3_ID3V24, MP3_ID3V1, FLAC_VORBIS,
    NO_EMBEDDED_TAGS, UNSUPPORTED_FORMAT, UNSUPPORTED_LAYOUT,
    INVALID_METADATA, FAST_BUDGET_EXCEEDED,
}

data class FastTagRead(
    val code: FastTagCode,
    val tags: AudioTagFields? = null,
    val embeddedTitle: Boolean = false,
    val embeddedArtist: Boolean = false,
    val embeddedAlbum: Boolean = false,
    val durationMs: Long? = null,
)

private class AlbumArtistCandidates {
    private var canonical: String? = null
    private var alias: String? = null

    fun add(key: String, value: String) {
        when (key) {
            "ALBUMARTIST" -> if (canonical == null) canonical = value
            "ALBUM ARTIST", "ALBUM_ARTIST" -> if (alias == null) alias = value
        }
    }

    fun preferred(): String? = canonical ?: alias
}

/**
 * Read only MP3 ID3v2.3/v2.4 or ID3v1 and native FLAC Vorbis Comments.
 *
 * No Android extractor, audio decoding, persistent cache, or hidden fallback to
 * a full-file reader. Only one caller-chosen file. At most six distinct 64-KiB
 * blocks and 96 KiB of requested metadata (actual GET uses 64-KiB blocks).
 * Large artwork is skipped by byte offsets without downloading its contents.
 *
 * Supported layouts: ID3v2.3 with an extended header, ID3v2.4 with an extended
 * header and/or tag-level and frame-level unsynchronisation, FLAC behind an
 * ID3v2 preamble, and a corrupt STREAMINFO followed by valid comments.
 *
 * ID3v2.4 tag-level unsynchronisation is walked, not decoded up front: a stored
 * frame size already counts the inserted zero bytes, so frames are read at
 * their stored offsets and only each frame body is de-unsynchronised. A
 * tag-level ID3v2.3 unsynchronised tag stays refused: that model sizes the tag
 * before unsynchronisation, so the byte count cannot be trusted without a
 * whole-tag copy the read budget cannot guarantee.
 */
class FastAudioTagReader {
    fun read(
        fileName: String,
        fileSize: Long,
        readAt: (Long, ByteArray, Int, Int) -> Int,
        readCachedAt: ((Long, ByteArray, Int, Int) -> Int)? = null,
    ): FastTagRead {
        val suffix = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val inputIssue = when {
            suffix != "mp3" && suffix != "flac" -> FastTagRead(FastTagCode.UNSUPPORTED_FORMAT)
            fileSize < 4L -> FastTagRead(FastTagCode.INVALID_METADATA)
            else -> null
        }
        if (inputIssue != null) return inputIssue
        val source = BoundedBytes(fileSize, readAt, readCachedAt)
        return try {
            if (suffix == "mp3") readMp3(source, fileName) else readFlac(source, fileName)
        } catch (stop: FastStop) {
            FastTagRead(stop.code)
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount", "MagicNumber")
    private fun readMp3(source: BoundedBytes, name: String): FastTagRead {
        val first = source.bytes(0L, minOf(ID3_HEADER_BYTES.toLong(), source.size).toInt())
        if (first.size < ID3_HEADER_BYTES || ascii(first, 0, 3) != "ID3") {
            if (source.size < 128) {
                return FastTagRead(FastTagCode.NO_EMBEDDED_TAGS,
                    durationMs = mp3DurationMs(source, 0L))
            }
            val end = source.bytes(source.size - 128L, 128)
            if (ascii(end, 0, 3) != "TAG") {
                return FastTagRead(FastTagCode.NO_EMBEDDED_TAGS,
                    durationMs = mp3DurationMs(source, 0L))
            }
            val title = latin1(end, 3, 30)
            val artist = latin1(end, 33, 30)
            val album = latin1(end, 63, 30)
            val genre = Id3Genres.fromNumber(u(end[127]))
            return result(FastTagCode.MP3_ID3V1, name, title, artist, album, null,
                rawGenre = genre, durationMs = mp3DurationMs(source, 0L))
        }

        val major = u(first[3])
        if (major !in 3..4 || u(first[4]) != 0) {
            return FastTagRead(FastTagCode.UNSUPPORTED_LAYOUT)
        }
        val flags = u(first[5])
        // v2.3: the extended header is understood. Tag-level unsynchronisation
        // sizes the tag before stuffing, so it stays refused under the budget.
        // v2.4: tag-level unsynchronisation is walked frame by frame, a footer
        // is not supported.
        val supported = if (major == 3) ID3_FLAG_EXTENDED_HEADER
            else ID3_FLAG_EXTENDED_HEADER or ID3_FLAG_UNSYNC
        if ((flags and ID3_HEADER_FLAG_MASK and supported.inv()) != 0) {
            return FastTagRead(FastTagCode.UNSUPPORTED_LAYOUT)
        }
        val tagSize = synchsafe(first, 6)
        val end = ID3_HEADER_BYTES + tagSize
        if (end > source.size) return FastTagRead(FastTagCode.INVALID_METADATA)
        // A v2.4 tag-level unsync flag does not move frames: every stored frame
        // size already counts the inserted 0x00 bytes, so the walk stays on raw
        // offsets and only a frame body that is decoded is de-unsynchronised.
        val tagLevelUnsynced = major == 4 && (flags and ID3_FLAG_UNSYNC) != 0
        val tags = streamingTag(source, flags, end, major, tagLevelUnsynced)
        return result(if (major == 3) FastTagCode.MP3_ID3V23 else FastTagCode.MP3_ID3V24,
            name, tags["TIT2"], tags["TPE1"], tags["TALB"], tags["TPE2"],
            rawYear = tags["TDRC"] ?: tags["TYER"],
            rawTrackNumber = tags["TRCK"],
            rawDiscNumber = tags["TPOS"],
            rawGenre = tags["TCON"],
            durationMs = mp3DurationMs(source, end))
    }

    /**
     * Walks frames on their stored offsets. Extended-header fields are read as
     * stored because unsynchronisation covers frame data, not the header.
     */
    private fun streamingTag(
        source: BoundedBytes, flags: Int, end: Long, major: Int,
        tagLevelUnsynced: Boolean = false,
    ): Map<String, String> {
        val read: (Long, Int) -> ByteArray = { at, count -> source.bytes(at, count) }
        var start = ID3_HEADER_BYTES.toLong()
        if ((flags and ID3_FLAG_EXTENDED_HEADER) != 0) {
            start = extendedHeaderEnd(read, start, end, major)
        }
        return collectId3Tags(major, start, end, tagLevelUnsynced, read)
    }

    @Suppress("MagicNumber", "ThrowsCount")
    private fun extendedHeaderEnd(
        read: (Long, Int) -> ByteArray, pos: Long, end: Long, major: Int,
    ): Long {
        if (pos + ID3_EXTENDED_HEADER_BYTES > end) {
            throw FastStop(FastTagCode.INVALID_METADATA)
        }
        val size = read(pos, ID3_EXTENDED_HEADER_BYTES)
        // v2.3 stores a plain size that excludes its own four bytes; v2.4 stores
        // a synchsafe size that already includes itself.
        val declared = if (major == 4) synchsafe(size, 0) else big32(size, 0)
        val next = pos + if (major == 4) declared else declared + ID3_EXTENDED_HEADER_BYTES
        if (declared < ID3_EXTENDED_HEADER_MIN || next > end) {
            throw FastStop(FastTagCode.INVALID_METADATA)
        }
        return next
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount", "ThrowsCount", "MagicNumber")
    private fun collectId3Tags(
        major: Int,
        start: Long,
        end: Long,
        tagLevelUnsynced: Boolean,
        read: (Long, Int) -> ByteArray,
    ): Map<String, String> {
        var pos = start
        var frames = 0
        val tags = linkedMapOf<String, String>()
        while (pos + ID3_FRAME_HEADER_BYTES <= end) {
            if (++frames > ID3_MAX_FRAMES) throw FastStop(FastTagCode.FAST_BUDGET_EXCEEDED)
            val header = read(pos, ID3_FRAME_HEADER_BYTES)
            if (header.all { it == 0.toByte() }) break
            val id = ascii(header, 0, 4)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) {
                throw FastStop(FastTagCode.INVALID_METADATA)
            }
            val length = if (major == 4) synchsafe(header, 4) else big32(header, 4)
            val next = pos + ID3_FRAME_HEADER_BYTES + length
            if (length <= 0L || next > end) throw FastStop(FastTagCode.INVALID_METADATA)
            if (id in wantedTextFrames && !tags.containsKey(id)) {
                val (skip, unsynchronised) = frameLayout(major, u(header[9]), tagLevelUnsynced)
                val payloadLength = length - skip
                if (payloadLength > ID3_MAX_TEXT_FRAME_BYTES) {
                    throw FastStop(FastTagCode.FAST_BUDGET_EXCEEDED)
                }
                val raw = read(pos + ID3_FRAME_HEADER_BYTES + skip, payloadLength.toInt())
                val payload = if (unsynchronised) deUnsynchronise(raw) else raw
                val value = decodeId3Text(payload, major)
                    ?: throw FastStop(FastTagCode.UNSUPPORTED_LAYOUT)
                if (value.isNotBlank()) tags[id] = value
            }
            // APIC/other frames can be huge: seek over them, never download them.
            pos = next
        }
        return tags
    }

    /**
     * Returns the payload prefix to skip and whether the payload needs de-unsync.
     * In v2.4 a tag-level flag unsynchronises every frame, so it is applied on
     * top of the frame flag instead of replacing the stored-offset walk.
     */
    @Suppress("MagicNumber", "ThrowsCount")
    private fun frameLayout(
        major: Int, formatFlags: Int, tagLevelUnsynced: Boolean,
    ): Pair<Int, Boolean> {
        val rejected = if (major == 4) {
            ID3_FRAME_GROUPING or ID3_FRAME_COMPRESSION or ID3_FRAME_ENCRYPTION
        } else {
            ID3_FRAME_V23_GROUPING or ID3_FRAME_V23_COMPRESSION or ID3_FRAME_V23_ENCRYPTION
        }
        if ((formatFlags and rejected) != 0) throw FastStop(FastTagCode.UNSUPPORTED_LAYOUT)
        val skip = if (major == 4 && (formatFlags and ID3_FRAME_DATA_LENGTH) != 0) {
            ID3_DATA_LENGTH_BYTES
        } else {
            0
        }
        val unsynchronised = major == 4 &&
            (tagLevelUnsynced || (formatFlags and ID3_FRAME_UNSYNC) != 0)
        return skip to unsynchronised
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod", "ThrowsCount", "MagicNumber")
    private fun readFlac(source: BoundedBytes, name: String): FastTagRead {
        val marker = flacMarkerOffset(source)
            ?: return FastTagRead(FastTagCode.UNSUPPORTED_LAYOUT)
        var pos = marker + FLAC_MARKER_BYTES
        var durationMs: Long? = null
        repeat(FLAC_MAX_BLOCKS) { index ->
            val header = flacBlockHeader(source, pos)
                ?: return FastTagRead(FastTagCode.INVALID_METADATA)
            val type = u(header[0]) and FLAC_TYPE_MASK
            val size = (u(header[1]) shl 16) or (u(header[2]) shl 8) or u(header[3])
            val contentStart = pos + FLAC_BLOCK_HEADER_BYTES
            if (type == FLAC_TYPE_INVALID || size.toLong() > source.size - contentStart) {
                return FastTagRead(FastTagCode.INVALID_METADATA)
            }
            val last = (u(header[0]) and FLAC_LAST_BLOCK) != 0
            if (index == 0) {
                if (type != FLAC_TYPE_STREAMINFO) {
                    return FastTagRead(FastTagCode.INVALID_METADATA)
                }
                val declaredEnd = contentStart + size
                val intact = last || flacHeaderIsPlausible(source, declaredEnd)
                durationMs = streamInfoDuration(source, contentStart, size, intact)
                if (last) {
                    return FastTagRead(FastTagCode.NO_EMBEDDED_TAGS,
                        durationMs = durationMs)
                }
                // A broken STREAMINFO must not cost the following comments:
                // resynchronise on the first plausible block inside its span.
                pos = if (intact) declaredEnd else {
                    locateFlacBlock(source, contentStart, declaredEnd)
                        ?: return FastTagRead(FastTagCode.INVALID_METADATA)
                }
            } else if (type == FLAC_TYPE_VORBIS) {
                if (size > FLAC_MAX_COMMENT_BYTES) {
                    return FastTagRead(FastTagCode.FAST_BUDGET_EXCEEDED)
                }
                val values = parseVorbis(source.bytes(contentStart, size))
                return result(FastTagCode.FLAC_VORBIS, name,
                    values["TITLE"], values["ARTIST"], values["ALBUM"], values["ALBUMARTIST"],
                    rawYear = values["DATE"] ?: values["YEAR"],
                    rawTrackNumber = values["TRACKNUMBER"],
                    rawDiscNumber = values["DISCNUMBER"],
                    rawGenre = values["GENRE"],
                    durationMs = durationMs)
            } else {
                pos = contentStart + size
                if (last) {
                    return FastTagRead(FastTagCode.NO_EMBEDDED_TAGS,
                        durationMs = durationMs)
                }
            }
        }
        return FastTagRead(FastTagCode.FAST_BUDGET_EXCEEDED)
    }

    /**
     * The first four bytes are read unconditionally to keep the common case at
     * one Range request. When `fLaC` is missing the offset is *computed* from the
     * ID3v2 header, because some taggers prepend an ID3v2 block to a native FLAC
     * stream: a valid header sizes the preamble exactly (10 + synchsafe size,
     * plus a v2.4 footer when flag 0x10 is set).
     *
     * A window search stays as the reserve: it runs when the size field is
     * missing, implausible or points at something that is not the marker. It
     * cannot be the primary rule, because the literal `fLaC` also occurs inside
     * ordinary tag frames (see `flac_id3_preamble.flac`, which carries such a
     * decoy), and it is limited to [FLAC_PROBE_BYTES].
     */
    @Suppress("MagicNumber", "ReturnCount")
    private fun flacMarkerOffset(source: BoundedBytes): Long? {
        val head = source.bytes(0L, minOf(FLAC_MARKER_BYTES.toLong(), source.size).toInt())
        if (ascii(head, 0, FLAC_MARKER_BYTES) == FLAC_MARKER) return 0L
        val declared = id3PreambleEnd(source)
        if (declared != null && markerAt(source, declared)) return declared
        val windowLength = minOf(FLAC_PROBE_BYTES.toLong(), source.size - FLAC_MARKER_BYTES)
        if (windowLength < FLAC_MARKER_BYTES) return null
        val window = String(source.bytes(0L, windowLength.toInt()), Charsets.ISO_8859_1)
        val found = window.indexOf(FLAC_MARKER)
        return if (found >= 0) found.toLong() else null
    }

    /**
     * End offset of a well-formed ID3v2 preamble, or null when the file does not
     * start with one. Purely a read of stored fields: nothing is decoded and
     * nothing is guessed, so a wrong size falls through to the window search.
     */
    @Suppress("MagicNumber", "ReturnCount")
    private fun id3PreambleEnd(source: BoundedBytes): Long? {
        if (source.size < ID3_HEADER_BYTES) return null
        val header = source.bytes(0L, ID3_HEADER_BYTES)
        if (ascii(header, 0, 3) != "ID3") return null
        val major = u(header[3])
        if (major !in 2..4) return null
        var size = 0L
        for (index in 6 until ID3_HEADER_BYTES) {
            val value = u(header[index])
            if (value and 0x80 != 0) return null
            size = (size shl 7) or value.toLong()
        }
        val footer = if (major == 4 && (u(header[5]) and ID3_FLAG_FOOTER) != 0) {
            ID3_HEADER_BYTES.toLong()
        } else {
            0L
        }
        val end = ID3_HEADER_BYTES + size + footer
        return if (end in 0L..source.size) end else null
    }

    /** True when the bytes at [at] are the `fLaC` marker, without a search. */
    @Suppress("MagicNumber")
    private fun markerAt(source: BoundedBytes, at: Long): Boolean {
        if (at < 0L || at > source.size - FLAC_MARKER_BYTES) return false
        return ascii(source.bytes(at, FLAC_MARKER_BYTES), 0, FLAC_MARKER_BYTES) == FLAC_MARKER
    }

    @Suppress("MagicNumber")
    private fun flacBlockHeader(source: BoundedBytes, at: Long): ByteArray? {
        if (at < 0L || at > source.size - FLAC_BLOCK_HEADER_BYTES) return null
        return source.bytes(at, FLAC_BLOCK_HEADER_BYTES)
    }

    @Suppress("MagicNumber", "ReturnCount")
    private fun flacHeaderIsPlausible(source: BoundedBytes, at: Long): Boolean {
        val header = flacBlockHeader(source, at) ?: return false
        val type = u(header[0]) and FLAC_TYPE_MASK
        if (type !in flacContinuationTypes) return false
        val size = (u(header[1]) shl 16) or (u(header[2]) shl 8) or u(header[3])
        return size.toLong() <= source.size - (at + FLAC_BLOCK_HEADER_BYTES)
    }

    @Suppress("MagicNumber", "ReturnCount")
    private fun locateFlacBlock(source: BoundedBytes, from: Long, until: Long): Long? {
        val length = minOf(until - from, FLAC_PROBE_BYTES.toLong())
        if (length < FLAC_BLOCK_HEADER_BYTES) return null
        val window = source.bytes(from, length.toInt())
        var index = 0
        while (index + FLAC_BLOCK_HEADER_BYTES <= window.size) {
            val type = u(window[index]) and FLAC_TYPE_MASK
            val size = (u(window[index + 1]) shl 16) or
                (u(window[index + 2]) shl 8) or u(window[index + 3])
            val contentStart = from + index + FLAC_BLOCK_HEADER_BYTES
            if (type in flacContinuationTypes && size.toLong() <= source.size - contentStart) {
                return from + index
            }
            index++
        }
        return null
    }

    /** Duration is trusted only for a spec-sized STREAMINFO inside an intact chain. */
    @Suppress("ReturnCount")
    private fun streamInfoDuration(
        source: BoundedBytes, contentStart: Long, size: Int, intact: Boolean,
    ): Long? {
        if (!intact || size != FLAC_STREAMINFO_BYTES) return null
        return source.cachedBytes(contentStart, FLAC_STREAMINFO_BYTES)?.let(::flacDurationMs)
    }

    private fun parseVorbis(data: ByteArray): Map<String, String> {
        var pos = 0
        fun count(): Int {
            if (pos > data.size - 4) throw FastStop(FastTagCode.INVALID_METADATA)
            val value = little32(data, pos)
            pos += 4
            if (value > (data.size - pos).toLong() && pos == 4) {
                // The first length is the vendor field.
                throw FastStop(FastTagCode.INVALID_METADATA)
            }
            return if (value > Int.MAX_VALUE) throw FastStop(FastTagCode.INVALID_METADATA)
                else value.toInt()
        }
        val vendorLength = count()
        if (vendorLength > data.size - pos) throw FastStop(FastTagCode.INVALID_METADATA)
        pos += vendorLength // No need to copy the vendor string.
        val entries = count()
        if (entries > 512) throw FastStop(FastTagCode.FAST_BUDGET_EXCEEDED)
        val result = linkedMapOf<String, String>()
        val albumArtist = AlbumArtistCandidates()
        repeat(entries) {
            val fieldLength = count()
            if (fieldLength > data.size - pos) throw FastStop(FastTagCode.INVALID_METADATA)
            val field = String(data, pos, fieldLength, Charsets.UTF_8)
            pos += fieldLength
            storeVorbisField(field, result, albumArtist)
        }
        albumArtist.preferred()?.let { result["ALBUMARTIST"] = it }
        return result
    }

    private fun storeVorbisField(
        field: String,
        result: MutableMap<String, String>,
        albumArtist: AlbumArtistCandidates,
    ) {
        val separator = field.indexOf('=')
        if (separator <= 0) return
        val key = field.substring(0, separator).uppercase(Locale.ROOT)
        val value = field.substring(separator + 1)
        when (key) {
            "ALBUMARTIST", "ALBUM ARTIST", "ALBUM_ARTIST" -> albumArtist.add(key, value)
            else -> if (key in wantedVorbisTextFields && !result.containsKey(key)) {
                result[key] = value
            }
        }
    }

    @Suppress("LongParameterList")
    private fun result(
        code: FastTagCode,
        fileName: String,
        title: String?,
        artist: String?,
        album: String?,
        albumArtist: String?,
        rawYear: String? = null,
        rawTrackNumber: String? = null,
        rawDiscNumber: String? = null,
        rawGenre: String? = null,
        durationMs: Long? = null,
    ): FastTagRead {
        if (listOf(title, artist, album, albumArtist, rawYear, rawTrackNumber,
                rawDiscNumber, rawGenre).none { !it.isNullOrBlank() }) {
            return FastTagRead(FastTagCode.NO_EMBEDDED_TAGS, durationMs = durationMs)
        }
        return FastTagRead(
            code = code,
            tags = AudioTagFields.fromRaw(
                title, artist, album, albumArtist, fileName,
                rawYear = rawYear, rawTrackNumber = rawTrackNumber,
                rawDiscNumber = rawDiscNumber,
                rawGenre = rawGenre,
            ),
            embeddedTitle = !title.isNullOrBlank(),
            embeddedArtist = !artist.isNullOrBlank(),
            embeddedAlbum = !album.isNullOrBlank(),
            durationMs = durationMs,
        )
    }

    /** Reads only bytes already present in the bounded Range reader's RAM block cache. */
    @Suppress("MagicNumber", "CyclomaticComplexMethod", "ReturnCount")
    private fun mp3DurationMs(source: BoundedBytes, frameStart: Long): Long? {
        val header = source.cachedBytes(frameStart, 4) ?: return null
        val bits = header.toUnsignedInt()
        if ((bits ushr 21) != 0x7FF) return null
        val version = (bits ushr 19) and 0x3
        val layer = (bits ushr 17) and 0x3
        val hasCrc = ((bits ushr 16) and 1) == 0
        val sampleRateIndex = (bits ushr 10) and 0x3
        val channelMode = (bits ushr 6) and 0x3
        if (version == 1 || layer != 1 || sampleRateIndex == 3) return null
        if (((bits ushr 12) and 0xF) in setOf(0, 15)) return null
        val sampleRates = intArrayOf(44_100, 48_000, 32_000)
        val sampleRate = when (version) {
            3 -> sampleRates[sampleRateIndex]
            2 -> sampleRates[sampleRateIndex] / 2
            else -> sampleRates[sampleRateIndex] / 4
        }
        val samplesPerFrame = if (version == 3) 1152 else 576
        val sideInfo = when {
            version == 3 && channelMode == 3 -> 17
            version == 3 -> 32
            channelMode == 3 -> 9
            else -> 17
        }
        val adjustedXingStart = frameStart + 4L +
            (if (hasCrc) 2L else 0L) + sideInfo
        val xing = source.cachedBytes(adjustedXingStart, 12)
        if (xing != null && (ascii(xing, 0, 4) == "Xing" || ascii(xing, 0, 4) == "Info")) {
            val flags = xing.readUnsignedInt(4)
            if ((flags and 1L) != 0L) {
                return durationMs(xing.readUnsignedInt(8), samplesPerFrame, sampleRate)
            }
        }
        val vbri = source.cachedBytes(frameStart + 36L, 18)
        if (vbri != null && ascii(vbri, 0, 4) == "VBRI") {
            return durationMs(vbri.readUnsignedInt(14), samplesPerFrame, sampleRate)
        }
        // No frame count in the stream: a single header cannot tell CBR from VBR,
        // so the following headers have to agree before the size is divided by a
        // frame length. Still not a single extra Range request: the headers are
        // taken from the block cache that already holds the first frame.
        if (!constantBitrateConfirmed(source, frameStart, bits)) return null
        return constantBitrateDurationMs(
            source.size - frameStart, bits, samplesPerFrame, sampleRate)
    }

    /**
     * True when [CBR_VERIFY_FRAMES] frames that follow the first one carry the
     * same version, layer, sample rate and bitrate. A stream whose headers keep
     * changing is VBR: its frame count cannot be derived from the file size, so
     * the caller must report an unknown duration instead of a wrong one.
     *
     * Only cached bytes are read, so a missing cache block is a reason to decline
     * (false), never a reason to issue another Range request.
     */
    private fun constantBitrateConfirmed(
        source: BoundedBytes, frameStart: Long, firstBits: Int,
    ): Boolean {
        var at = frameStart + (layer3FrameBytes(firstBits) ?: return false)
        var confirmed = true
        var checked = 0
        while (checked < CBR_VERIFY_FRAMES && confirmed) {
            val bits = source.cachedBytes(at, 4)?.toUnsignedInt()
            val frameBytes = if (bits == null) null else layer3FrameBytes(bits)
            if (bits == null || frameBytes == null) {
                confirmed = false
            } else {
                confirmed = (bits and MPEG_STREAM_MASK) == (firstBits and MPEG_STREAM_MASK)
                at += frameBytes
            }
            checked++
        }
        return confirmed
    }

    /**
     * Stored size of one layer III frame in bytes, or null when [headerBits] is
     * not a valid MPEG-1/2 layer III header. Pure arithmetic: no I/O, so it can
     * be used on a cached header of any frame.
     */
    @Suppress("MagicNumber", "ReturnCount")
    private fun layer3FrameBytes(headerBits: Int): Long? {
        val version = (headerBits ushr 19) and 0x3
        val sampleRateIndex = (headerBits ushr 10) and 0x3
        if (version == 1 || ((headerBits ushr 17) and 0x3) != 1 || sampleRateIndex == 3) {
            return null
        }
        val index = (headerBits ushr 12) and 0xF
        val kbps = if (version == 3) mpeg1Layer3Kbps.getOrNull(index)
        else mpeg2Layer3Kbps.getOrNull(index)
        if (kbps == null || kbps <= 0) return null
        val coefficient = if (version == 3) MPEG1_LAYER3_COEFFICIENT else MPEG2_LAYER3_COEFFICIENT
        val baseRate = intArrayOf(44_100, 48_000, 32_000)[sampleRateIndex]
        val sampleRate = when (version) {
            3 -> baseRate
            2 -> baseRate / 2
            else -> baseRate / 4
        }
        val padding = (headerBits ushr 9) and 1
        val bytes = coefficient * kbps * MILLIS_PER_SECOND / sampleRate + padding
        return bytes.takeIf { it > 0L }
    }

    private fun constantBitrateDurationMs(
        audioBytes: Long, headerBits: Int, samplesPerFrame: Int, sampleRate: Int,
    ): Long? {
        val frameBytes = layer3FrameBytes(headerBits)
        if (audioBytes <= 0L || sampleRate <= 0 || frameBytes == null) return null
        return durationMs(audioBytes / frameBytes, samplesPerFrame, sampleRate)
    }

    private fun durationMs(frames: Long, samplesPerFrame: Int, sampleRate: Int): Long? {
        if (frames <= 0L || sampleRate <= 0) return null
        val duration = frames * samplesPerFrame * MILLIS_PER_SECOND / sampleRate
        return duration.takeIf { it > 0L }
    }

    @Suppress("MagicNumber")
    private fun flacDurationMs(streamInfo: ByteArray): Long? {
        if (streamInfo.size != 34) return null
        var packed = 0L
        for (index in 10 until 18) {
            packed = (packed shl 8) or (streamInfo[index].toLong() and 0xFFL)
        }
        val sampleRate = ((packed ushr 44) and 0xFFFFFL).toInt()
        val totalSamples = packed and 0xFFFFFFFFFL
        return if (sampleRate <= 0 || totalSamples <= 0L) null
        else (totalSamples * MILLIS_PER_SECOND / sampleRate).takeIf { it > 0L }
    }

    private fun ByteArray.toUnsignedInt(): Int =
        ((u(this[0]) shl 24) or (u(this[1]) shl 16) or
            (u(this[2]) shl 8) or u(this[3]))

    private fun ByteArray.readUnsignedInt(at: Int): Long =
        (u(this[at]).toLong() shl 24) or (u(this[at + 1]).toLong() shl 16) or
            (u(this[at + 2]).toLong() shl 8) or u(this[at + 3]).toLong()

    private fun decodeId3Text(payload: ByteArray, version: Int): String? {
        if (payload.isEmpty()) return ""
        val encoding = u(payload[0])
        val data = payload.copyOfRange(1, payload.size)
        val charset = when (encoding) {
            0 -> null
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> if (version == 4) Charsets.UTF_8 else return null
            else -> return null
        }
        val text = if (charset == null) legacySingleByteText(data) else String(data, charset)
        return text.substringBefore('\u0000').trim()
    }

    /**
     * Encoding byte 0 means ISO-8859-1, but many real taggers write Windows-1251
     * bytes there while keeping the declared encoding. The decision is byte-local
     * and deterministic: read the payload as Windows-1251 only when that reading
     * contains at least [CP1251_MIN_CYRILLIC_LETTERS] Cyrillic letters and no
     * word mixes Cyrillic with non-Cyrillic letters. Nothing is guessed from the
     * file name or locale.
     *
     * Mixed content stays detectable: ASCII words, quotes (`« » „ “ ”`), dashes
     * (`– —`) and Ukrainian letters (`і ї є ґ`) do not switch detection off. A
     * word that spawns both scripts after decoding (for example Latin-1 `Grüße`
     * read as `GrьЯe`) means the payload was really ISO-8859-1.
     *
     * Known limitation: a payload built only from high Latin-1 letters (for
     * example `ÅÄÖÅÄÖ`) is byte-identical to a CP1251 Cyrillic word and is
     * decoded as Cyrillic; the two encodings cannot be separated without
     * guessing.
     */
    @Suppress("ReturnCount")
    private fun legacySingleByteText(data: ByteArray): String {
        val latin1 = String(data, Charsets.ISO_8859_1)
        if (data.none { u(it) >= CP1251_HIGH_BYTE_START }) return latin1
        val cyrillic = String(data, windows1251)
        if (cyrillic.count { it in cyrillicLetters } < CP1251_MIN_CYRILLIC_LETTERS) {
            return latin1
        }
        if (hasMixedScriptWord(cyrillic)) return latin1
        return cyrillic
    }

    /** True when one word holds both Cyrillic and non-Cyrillic letters. */
    private fun hasMixedScriptWord(text: String): Boolean {
        var cyrillic = false
        var other = false
        for (letter in text) {
            if (!letter.isLetter()) {
                cyrillic = false
                other = false
                continue
            }
            if (letter in cyrillicLetters) cyrillic = true else other = true
            if (cyrillic && other) return true
        }
        return false
    }

    /**
     * Removes ID3 unsynchronisation: a 0x00 inserted after 0xFF keeps the data
     * from looking like an MPEG sync word.
     *
     * Writers are allowed to stuff every 0xFF, not only the ones that would form
     * a sync, so a 0x00 that directly follows 0xFF is stuffing and is dropped.
     * Nothing else is removed: a 0xFF followed by an ordinary byte keeps that
     * byte, so a decoder can never eat real payload. Dropping only the narrow
     * `FF 00` / `FF E0..FF` shapes loses the byte after a stuffer that escaped
     * every 0xFF and silently truncates the text at the first such point.
     */
    @Suppress("MagicNumber")
    private fun deUnsynchronise(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size)
        var index = 0
        while (index < data.size) {
            val value = u(data[index])
            out.write(value)
            index++
            if (value == 0xFF && index < data.size && u(data[index]) == 0x00) index++
        }
        return out.toByteArray()
    }

    private fun synchsafe(bytes: ByteArray, at: Int): Long {
        var value = 0L
        for (i in at until at + 4) {
            val b = u(bytes[i])
            if (b and 0x80 != 0) throw FastStop(FastTagCode.INVALID_METADATA)
            value = (value shl 7) or b.toLong()
        }
        return value
    }

    private fun big32(bytes: ByteArray, at: Int): Long =
        (u(bytes[at]).toLong() shl 24) or
        (u(bytes[at + 1]).toLong() shl 16) or
        (u(bytes[at + 2]).toLong() shl 8) or u(bytes[at + 3]).toLong()

    private fun little32(bytes: ByteArray, at: Int): Long =
        u(bytes[at]).toLong() or
        (u(bytes[at + 1]).toLong() shl 8) or
        (u(bytes[at + 2]).toLong() shl 16) or (u(bytes[at + 3]).toLong() shl 24)

    private fun ascii(data: ByteArray, start: Int, n: Int) =
        String(data, start, n, Charsets.US_ASCII)

    private fun latin1(data: ByteArray, start: Int, n: Int) =
        String(data, start, n, Charsets.ISO_8859_1).trimEnd('\u0000', ' ')

    private fun u(value: Byte): Int = value.toInt() and 0xFF

    private class FastStop(val code: FastTagCode) : RuntimeException()

    private class BoundedBytes(
        val size: Long,
        private val readAt: (Long, ByteArray, Int, Int) -> Int,
        private val readCachedAt: ((Long, ByteArray, Int, Int) -> Int)?,
    ) {
        private val blocks = hashSetOf<Long>()
        private var bytesRequested = 0
        fun bytes(position: Long, count: Int): ByteArray {
            if (count < 0 || position < 0 || position > size ||
                count.toLong() > size - position) throw FastStop(FastTagCode.INVALID_METADATA)
            if (count > 65536 || bytesRequested > 98304 - count) {
                throw FastStop(FastTagCode.FAST_BUDGET_EXCEEDED)
            }
            val dest = ByteArray(count)
            var copied = 0
            while (copied < count) {
                val absolute = position + copied
                val block = absolute / 65536L
                if (block !in blocks) {
                    if (blocks.size == 6) throw FastStop(FastTagCode.FAST_BUDGET_EXCEEDED)
                    blocks.add(block)
                }
                val piece = minOf(count - copied, (65536L - (absolute % 65536L)).toInt())
                val got = readAt(absolute, dest, copied, piece)
                if (got !in 1..piece) throw IOException("Metadata Range unavailable")
                copied += got
            }
            bytesRequested += count
            return dest
        }

        @Suppress("ReturnCount")
        fun cachedBytes(position: Long, count: Int): ByteArray? {
            val cachedReader = readCachedAt ?: return null
            if (count < 0 || position < 0 || position > size ||
                count.toLong() > size - position) return null
            val dest = ByteArray(count)
            return try {
                if (cachedReader(position, dest, 0, count) == count) dest else null
            } catch (_: Exception) {
                null
            }
        }
    }
}
