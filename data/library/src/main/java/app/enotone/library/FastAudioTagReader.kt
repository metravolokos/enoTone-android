package app.enotone.library

import java.io.IOException
import java.util.Locale

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
    ): FastTagRead {
        val suffix = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (suffix != "mp3" && suffix != "flac") {
            return FastTagRead(FastTagCode.UNSUPPORTED_FORMAT)
        }
        if (fileSize < 4L) return FastTagRead(FastTagCode.INVALID_METADATA)
        val source = BoundedBytes(fileSize, readAt)
        return try {
            if (suffix == "mp3") readMp3(source, fileName) else readFlac(source, fileName)
        } catch (stop: FastStop) {
            FastTagRead(stop.code)
        }
    }

    private fun readMp3(source: BoundedBytes, name: String): FastTagRead {
        val first = source.bytes(0L, minOf(10L, source.size).toInt())
        if (first.size < 10 || ascii(first, 0, 3) != "ID3") {
            if (source.size < 128) return FastTagRead(FastTagCode.NO_EMBEDDED_TAGS)
            val end = source.bytes(source.size - 128L, 128)
            if (ascii(end, 0, 3) != "TAG") return FastTagRead(FastTagCode.NO_EMBEDDED_TAGS)
            val title = latin1(end, 3, 30)
            val artist = latin1(end, 33, 30)
            val album = latin1(end, 63, 30)
            val genre = Id3Genres.fromNumber(u(end[127]))
            return result(FastTagCode.MP3_ID3V1, name, title, artist, album, null,
                rawGenre = genre)
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
            rawGenre = tags["TCON"])
    }

    private fun readFlac(source: BoundedBytes, name: String): FastTagRead {
        if (ascii(source.bytes(0, 4), 0, 4) != "fLaC") {
            return FastTagRead(FastTagCode.UNSUPPORTED_LAYOUT)
        }
        var pos = 4L
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
            if (type == 4) {
                if (size > 65536) return FastTagRead(FastTagCode.FAST_BUDGET_EXCEEDED)
                val data = source.bytes(contentStart, size)
                val values = parseVorbis(data)
                return result(FastTagCode.FLAC_VORBIS, name,
                    values["TITLE"], values["ARTIST"], values["ALBUM"], values["ALBUMARTIST"],
                    rawYear = values["DATE"] ?: values["YEAR"],
                    rawTrackNumber = values["TRACKNUMBER"],
                    rawDiscNumber = values["DISCNUMBER"],
                    rawGenre = values["GENRE"])
            }
            // PICTURE, padding, seek table: skip by encoded size, no bulk reads.
            pos = contentStart + size
            if (last) return FastTagRead(FastTagCode.NO_EMBEDDED_TAGS)
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
    ): FastTagRead {
        if (listOf(title, artist, album, albumArtist, rawYear, rawTrackNumber,
                rawDiscNumber, rawGenre).none { !it.isNullOrBlank() }) {
            return FastTagRead(FastTagCode.NO_EMBEDDED_TAGS)
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
        )
    }

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
    }
}
