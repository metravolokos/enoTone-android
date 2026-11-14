package app.enotone.library

import java.io.IOException
import java.util.Locale

private const val MILLIS_PER_SECOND = 1_000L

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

/**
 * Read only MP3 ID3v2.3/v2.4 or ID3v1 and native FLAC Vorbis Comments.
 *
 * No Android extractor, audio decoding, persistent cache, or hidden fallback to
 * a full-file reader. Only one caller-chosen file. At most six distinct 64-KiB
 * blocks and 96 KiB of requested metadata (actual GET uses 64-KiB blocks).
 * Large artwork is skipped by byte offsets without downloading its contents.
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

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    private fun readMp3(source: BoundedBytes, name: String): FastTagRead {
        val first = source.bytes(0L, minOf(10L, source.size).toInt())
        if (first.size < 10 || ascii(first, 0, 3) != "ID3") {
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
        // Unsynchronisation, extended header, and compressed/encrypted layouts
        // must be handled in a separate tested parser before being accepted.
        if ((u(first[5]) and 0xF0) != 0) return FastTagRead(FastTagCode.UNSUPPORTED_LAYOUT)
        val tagSize = synchsafe(first, 6)
        val end = 10L + tagSize
        if (end > source.size) return FastTagRead(FastTagCode.INVALID_METADATA)
        var pos = 10L
        var frames = 0
        val tags = mutableMapOf<String, String>()
        while (pos + 10L <= end) {
            if (++frames > 256) return FastTagRead(FastTagCode.FAST_BUDGET_EXCEEDED)
            val hdr = source.bytes(pos, 10)
            if (hdr.slice(0..3).all { it == 0.toByte() }) break
            val id = ascii(hdr, 0, 4)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) {
                return FastTagRead(FastTagCode.INVALID_METADATA)
            }
            val length = if (major == 4) synchsafe(hdr, 4) else big32(hdr, 4)
            val next = pos + 10L + length
            if (length <= 0L || next > end) return FastTagRead(FastTagCode.INVALID_METADATA)
            if (id in setOf("TIT2", "TPE1", "TALB", "TPE2",
                "TDRC", "TYER", "TRCK", "TPOS", "TCON") && !tags.containsKey(id)) {
                // Ignore tag layouts requiring decrypt/decompress/unsynchronisation.
                if (u(hdr[8]) != 0 || u(hdr[9]) != 0) {
                    return FastTagRead(FastTagCode.UNSUPPORTED_LAYOUT)
                }
                if (length > 8192L) return FastTagRead(FastTagCode.FAST_BUDGET_EXCEEDED)
                val payload = source.bytes(pos + 10L, length.toInt())
                val value = decodeId3Text(payload, major)
                    ?: return FastTagRead(FastTagCode.UNSUPPORTED_LAYOUT)
                if (value.isNotBlank()) tags[id] = value
            }
            // APIC/other frames can be huge: seek over them, never download them.
            pos = next
        }
        return result(if (major == 3) FastTagCode.MP3_ID3V23 else FastTagCode.MP3_ID3V24,
            name, tags["TIT2"], tags["TPE1"], tags["TALB"], tags["TPE2"],
            rawYear = tags["TDRC"] ?: tags["TYER"],
            rawTrackNumber = tags["TRCK"],
            rawDiscNumber = tags["TPOS"],
            rawGenre = tags["TCON"],
            durationMs = mp3DurationMs(source, end))
    }

    private fun readFlac(source: BoundedBytes, name: String): FastTagRead {
        if (ascii(source.bytes(0, 4), 0, 4) != "fLaC") {
            return FastTagRead(FastTagCode.UNSUPPORTED_LAYOUT)
        }
        var pos = 4L
        var durationMs: Long? = null
        repeat(128) { index ->
            if (pos > source.size - 4) return FastTagRead(FastTagCode.INVALID_METADATA)
            val hdr = source.bytes(pos, 4)
            val last = (u(hdr[0]) and 0x80) != 0
            val type = u(hdr[0]) and 0x7F
            val size = (u(hdr[1]) shl 16) or (u(hdr[2]) shl 8) or u(hdr[3])
            // FLAC requires STREAMINFO to be the first block, of size 34.
            if (index == 0 && (type != 0 || size != 34)) {
                return FastTagRead(FastTagCode.INVALID_METADATA)
            }
            val contentStart = pos + 4
            if (size.toLong() > source.size - contentStart || type == 127) {
                return FastTagRead(FastTagCode.INVALID_METADATA)
            }
            if (index == 0) {
                durationMs = source.cachedBytes(contentStart, 34)?.let(::flacDurationMs)
            }
            if (type == 4) {
                if (size > 65536) return FastTagRead(FastTagCode.FAST_BUDGET_EXCEEDED)
                val data = source.bytes(contentStart, size)
                val values = parseVorbis(data)
                return result(FastTagCode.FLAC_VORBIS, name,
                    values["TITLE"], values["ARTIST"], values["ALBUM"], values["ALBUMARTIST"],
                    rawYear = values["DATE"] ?: values["YEAR"],
                    rawTrackNumber = values["TRACKNUMBER"],
                    rawDiscNumber = values["DISCNUMBER"],
                    rawGenre = values["GENRE"],
                    durationMs = durationMs)
            }
            // PICTURE, padding, seek table: skip by encoded size, no bulk reads.
            pos = contentStart + size
            if (last) return FastTagRead(FastTagCode.NO_EMBEDDED_TAGS,
                durationMs = durationMs)
        }
        return FastTagRead(FastTagCode.FAST_BUDGET_EXCEEDED)
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
        repeat(entries) {
            val fieldLength = count()
            if (fieldLength > data.size - pos) throw FastStop(FastTagCode.INVALID_METADATA)
            val field = String(data, pos, fieldLength, Charsets.UTF_8)
            pos += fieldLength
            val separator = field.indexOf('=')
            if (separator > 0) {
                // FLAC Vorbis uses ALBUMARTIST most often, but real libraries
                // also contain ALBUM ARTIST and ALBUM_ARTIST. Normalize the
                // aliases before storing without ever substituting track ARTIST.
                val rawKey = field.substring(0, separator).uppercase(Locale.ROOT)
                val key = when (rawKey) {
                    "ALBUM ARTIST", "ALBUM_ARTIST" -> "ALBUMARTIST"
                    else -> rawKey
                }
                if (key in setOf("TITLE", "ARTIST", "ALBUM", "ALBUMARTIST",
                    "DATE", "YEAR", "TRACKNUMBER", "DISCNUMBER", "GENRE") &&
                    !result.containsKey(key)) {
                    result[key] = field.substring(separator + 1)
                }
            }
        }
        return result
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
        val vbri = source.cachedBytes(frameStart + 36L, 18) ?: return null
        if (ascii(vbri, 0, 4) != "VBRI") return null
        return durationMs(vbri.readUnsignedInt(14), samplesPerFrame, sampleRate)
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
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> if (version == 4) Charsets.UTF_8 else return null
            else -> return null
        }
        return String(data, charset).substringBefore('\u0000').trim()
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
