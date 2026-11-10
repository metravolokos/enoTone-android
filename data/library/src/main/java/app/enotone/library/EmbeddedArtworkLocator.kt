package app.enotone.library

import java.io.IOException
import java.util.Locale

/**
 * Byte-range descriptor ONLY. The caller may fetch [length] bytes from [offset]
 * after user-visible demand and separate image decode/size validation.
 * No remote paths, account identifiers or image bytes enter this result.
 */
data class EmbeddedArtwork(
    val offset: Long,
    val length: Int,
    val mimeType: String,
    val pictureType: Int,
)

enum class ArtworkLookupCode {
    FOUND, NOT_FOUND, UNSUPPORTED_FORMAT, UNSUPPORTED_LAYOUT,
    INVALID_METADATA, LIMIT_EXCEEDED, READ_FAILED,
}

data class ArtworkLookup(
    val code: ArtworkLookupCode,
    val artwork: EmbeddedArtwork? = null,
)

/**
 * Locates cover artwork inside MP3 ID3v2.3/2.4 APIC or native FLAC PICTURE.
 *
 * Does NOT download the image, mutate Room, inspect unscanned tracks, or fall back
 * to a full audio transfer. MP3 text indexing remains completely independent.
 * The passed readAt must be a bounded, authenticated WebDAV Range source.
 */
class EmbeddedArtworkLocator {
    fun locate(
        fileName: String,
        fileSize: Long,
        readAt: (Long, ByteArray, Int, Int) -> Int,
    ): ArtworkLookup {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext !in setOf("mp3", "flac")) return ArtworkLookup(ArtworkLookupCode.UNSUPPORTED_FORMAT)
        if (fileSize < 10L) return ArtworkLookup(ArtworkLookupCode.INVALID_METADATA)
        val src = BoundedSource(fileSize, readAt)
        return try {
            val result = if (ext == "mp3") locateMp3(src) else locateFlac(src)
            if (result == null) ArtworkLookup(ArtworkLookupCode.NOT_FOUND)
            else ArtworkLookup(ArtworkLookupCode.FOUND, result)
        } catch (e: Stop) {
            ArtworkLookup(e.code)
        } catch (_: IOException) {
            ArtworkLookup(ArtworkLookupCode.READ_FAILED)
        }
    }

    private fun locateMp3(src: BoundedSource): EmbeddedArtwork? {
        val header = src.read(0L, 10)
        if (ascii(header, 0, 3) != "ID3") return null
        val version = u(header[3])
        if (version !in 3..4 || u(header[4]) != 0 ||
            (u(header[5]) and 0xF0) != 0) {
            throw Stop(ArtworkLookupCode.UNSUPPORTED_LAYOUT)
        }
        val tagEnd = checkedEnd(10L, synchsafe(header, 6), src.size)
        if (tagEnd > MAX_METADATA_POSITION) throw Stop(ArtworkLookupCode.LIMIT_EXCEEDED)
        var pos = 10L
        var fallback: EmbeddedArtwork? = null
        var frames = 0
        while (pos + 10L <= tagEnd) {
            if (++frames > MAX_FRAMES) throw Stop(ArtworkLookupCode.LIMIT_EXCEEDED)
            val frame = src.read(pos, 10)
            if (frame.sliceArray(0..3).all { it == 0.toByte() }) break
            val id = ascii(frame, 0, 4)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) {
                throw Stop(ArtworkLookupCode.INVALID_METADATA)
            }
            val size = if (version == 4) synchsafe(frame, 4)
                else big32(frame, 4)
            val start = pos + 10L
            val end = checkedEnd(start, size, tagEnd)
            if (size <= 0L) throw Stop(ArtworkLookupCode.INVALID_METADATA)
            if (id == "APIC") {
                // Compressed/encrypted/grouped frames and per-frame unsync are
                // not safely supported. Do not misinterpret their byte offsets.
                if (u(frame[8]) != 0 || u(frame[9]) != 0) {
                    throw Stop(ArtworkLookupCode.UNSUPPORTED_LAYOUT)
                }
                val artwork = parseApic(src, start, size)
                if (artwork != null) {
                    if (artwork.pictureType == FRONT_COVER) return artwork
                    if (fallback == null) fallback = artwork
                }
            }
            pos = end
        }
        return fallback
    }

    private fun parseApic(src: BoundedSource, start: Long, frameSize: Long): EmbeddedArtwork? {
        if (frameSize < 7L) return null
        // APIC description can be UTF-16; inspect at most 2 KiB of its prefix.
        val head = src.read(start, minOf(frameSize, MAX_APIC_PREFIX.toLong()).toInt())
        val encoding = u(head[0])
        if (encoding !in 0..3) throw Stop(ArtworkLookupCode.UNSUPPORTED_LAYOUT)
        val mimeEnd = head.indexOf(0.toByte(), startIndex = 1)
        if (mimeEnd !in 2..96 || mimeEnd + 2 >= head.size) return null
        val mime = normalizeMime(ascii(head, 1, mimeEnd - 1)) ?: return null
        val type = u(head[mimeEnd + 1])
        val descStart = mimeEnd + 2
        val contentStart = when (encoding) {
            0, 3 -> {
                val end = head.indexOf(0.toByte(), startIndex = descStart)
                if (end < 0) return null else end + 1
            }
            else -> {
                var end = -1
                var i = descStart
                while (i + 1 < head.size) {
                    if (head[i] == 0.toByte() && head[i + 1] == 0.toByte()) {
                        end = i
                        break
                    }
                    i += 2
                }
                if (end < 0) return null else end + 2
            }
        }
        val imageStart = start + contentStart
        val imageLength = frameSize - contentStart
        return candidate(imageStart, imageLength, mime, type, src.size)
    }

    private fun locateFlac(src: BoundedSource): EmbeddedArtwork? {
        if (ascii(src.read(0L, 4), 0, 4) != "fLaC") {
            throw Stop(ArtworkLookupCode.UNSUPPORTED_LAYOUT)
        }
        var pos = 4L
        var fallback: EmbeddedArtwork? = null
        repeat(MAX_BLOCKS) { index ->
            if (pos + 4L > src.size || pos > MAX_METADATA_POSITION) {
                throw Stop(ArtworkLookupCode.INVALID_METADATA)
            }
            val header = src.read(pos, 4)
            val last = (u(header[0]) and 0x80) != 0
            val type = u(header[0]) and 0x7F
            val size = (u(header[1]) shl 16) or (u(header[2]) shl 8) or u(header[3])
            if (index == 0 && (type != 0 || size != 34)) {
                throw Stop(ArtworkLookupCode.INVALID_METADATA)
            }
            if (type == 127) throw Stop(ArtworkLookupCode.INVALID_METADATA)
            val begin = pos + 4L
            val end = checkedEnd(begin, size.toLong(), src.size)
            if (end > MAX_METADATA_POSITION) {
                throw Stop(ArtworkLookupCode.LIMIT_EXCEEDED)
            }
            if (type == 6) {
                val artwork = parsePicture(src, begin, end)
                if (artwork != null) {
                    if (artwork.pictureType == FRONT_COVER) return artwork
                    if (fallback == null) fallback = artwork
                }
            }
            pos = end
            if (last) return fallback
        }
        throw Stop(ArtworkLookupCode.LIMIT_EXCEEDED)
    }

    private fun parsePicture(src: BoundedSource, start: Long, end: Long): EmbeddedArtwork? {
        var at = start
        fun field(): Long {
            if (at + 4L > end) throw Stop(ArtworkLookupCode.INVALID_METADATA)
            val value = big32(src.read(at, 4))
            at += 4L
            return value
        }
        val type = field().toInt()
        val mimeSize = field()
        if (mimeSize !in 1L..96L || mimeSize > end - at) return null
        val mime = normalizeMime(ascii(src.read(at, mimeSize.toInt()), 0,
            mimeSize.toInt())) ?: return null
        at += mimeSize
        val descriptionSize = field()
        if (descriptionSize > 4096L || descriptionSize > end - at) return null
        // Description is not needed to locate binary image; skip without GET.
        at += descriptionSize
        val width = field()
        val height = field()
        field() // bit depth
        field() // number of indexed colors
        val length = field()
        if (width > 8192L || height > 8192L ||
            (width > 0L && height > 0L && width * height > 40_000_000L)) return null
        if (at + length != end) return null
        return candidate(at, length, mime, type, src.size)
    }

    private fun candidate(
        offset: Long, length: Long, mime: String, type: Int, size: Long,
    ): EmbeddedArtwork? {
        if (length !in 1L..MAX_IMAGE_BYTES ||
            offset < 0L || offset > size || length > size - offset) return null
        return EmbeddedArtwork(offset, length.toInt(), mime, type)
    }

    private fun normalizeMime(raw: String): String? = when (raw.trim().lowercase(Locale.ROOT)) {
        "image/jpeg", "image/jpg" -> "image/jpeg"
        "image/png" -> "image/png"
        "image/webp" -> "image/webp"
        else -> null
    }

    private fun checkedEnd(start: Long, length: Long, boundary: Long): Long {
        if (length < 0L || start < 0L || start > boundary || length > boundary - start) {
            throw Stop(ArtworkLookupCode.INVALID_METADATA)
        }
        return start + length
    }

    private fun synchsafe(bytes: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in offset until offset + 4) {
            val part = u(bytes[i])
            if ((part and 0x80) != 0) throw Stop(ArtworkLookupCode.INVALID_METADATA)
            result = (result shl 7) or part.toLong()
        }
        return result
    }

    private fun big32(bytes: ByteArray, offset: Int = 0): Long =
        (u(bytes[offset]).toLong() shl 24) or
        (u(bytes[offset + 1]).toLong() shl 16) or
        (u(bytes[offset + 2]).toLong() shl 8) or u(bytes[offset + 3]).toLong()

    private fun ascii(bytes: ByteArray, start: Int, count: Int): String =
        String(bytes, start, count, Charsets.US_ASCII)

    private fun u(byte: Byte) = byte.toInt() and 0xFF

    private class Stop(val code: ArtworkLookupCode) : RuntimeException()

    private class BoundedSource(
        val size: Long,
        private val readAt: (Long, ByteArray, Int, Int) -> Int,
    ) {
        private val blocks = hashSetOf<Long>()
        private var bytesRead = 0

        fun read(position: Long, length: Int): ByteArray {
            if (position < 0L || length < 0 || position > size ||
                length.toLong() > size - position) {
                throw Stop(ArtworkLookupCode.INVALID_METADATA)
            }
            if (length > MAX_DESCRIPTOR_READ || bytesRead > MAX_PROBE_BYTES - length) {
                throw Stop(ArtworkLookupCode.LIMIT_EXCEEDED)
            }
            val result = ByteArray(length)
            var copied = 0
            while (copied < length) {
                val at = position + copied
                val block = at / NETWORK_BLOCK
                if (blocks.add(block) && blocks.size > MAX_PROBE_BLOCKS) {
                    throw Stop(ArtworkLookupCode.LIMIT_EXCEEDED)
                }
                val count = minOf(length - copied,
                    (NETWORK_BLOCK - at % NETWORK_BLOCK).toInt())
                val n = readAt(at, result, copied, count)
                if (n !in 1..count) throw IOException("Range unavailable")
                copied += n
            }
            bytesRead += length
            return result
        }
    }

    private companion object {
        const val FRONT_COVER = 3
        const val MAX_FRAMES = 256
        const val MAX_BLOCKS = 128
        const val MAX_DESCRIPTOR_READ = 2048
        const val MAX_APIC_PREFIX = 2048
        const val MAX_PROBE_BYTES = 96 * 1024
        const val MAX_PROBE_BLOCKS = 12
        const val NETWORK_BLOCK = 65536L
        const val MAX_METADATA_POSITION = 16L * 1024 * 1024
        const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
    }
}
