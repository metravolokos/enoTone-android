package app.enotone.library

import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Whitelisted codes only: no URL, remote path, ETag or exception messages. */
enum class RangeReadIssue {
    LIMIT, HTTP_STATUS, MISSING_CONTENT_RANGE, BAD_CONTENT_RANGE,
    BAD_ENCODING, CHANGED_ETAG, SHORT_BODY, OVERSIZED_BODY, NETWORK,
}

/** Classification of an unexpected Content-Range, never an untrusted header value. */
enum class ContentRangeMismatchKind {
    FORMAT, FORMAT_EQUALS_SEPARATOR, FORMAT_UNEXPECTED_UNIT,
    START, END, TOTAL, MULTIPLE, INVALID_BOUNDS,
}

/** Only numeric metadata; no raw URLs, paths, headers, ETags or file names. */
data class ContentRangeMismatch(
    val kind: ContentRangeMismatchKind,
    val expectedStart: Long,
    val expectedEnd: Long,
    val expectedTotal: Long,
    val receivedStart: Long? = null,
    val receivedEnd: Long? = null,
    val receivedTotal: Long? = null,
)

data class RangeReadStats(
    val requests: Int,
    val downloadedBytes: Long,
    val httpStatus: Int? = null,
    val issue: RangeReadIssue? = null,
    val rangeMismatch: ContentRangeMismatch? = null,
    val unknownTotalResponses: Int = 0,
)

/** For ONE manually requested track; never a playback transport. 64 KiB RAM block,
 * 8 MiB / 128 GET / 40 second limits, HTTP 206 + exact Content-Range only.
 * No log output, redirects, network secrets on disk, or persistent audio cache.
 */
class WebDavRangeReader(
    libraryRoot: String = "https://webdav.cloud.mail.ru/AllMusic/",
    encodedPath: String,
    val fileSize: Long,
    email: String,
    appPassword: String,
    etag: String? = null,
    allowLoopbackHttpForTests: Boolean = false,
) : Closeable {
    private val url: java.net.URL
    private var auth: String?
    private val etagStrong = etag?.takeIf {
        it.length in 2..256 && it.first() == '"' && it.last() == '"' &&
            it.all { c -> c.code in 32..126 }
    }
    private var closed = false
    private val started = System.nanoTime()
    private var calls = 0
    private var bytesFetched = 0L
    private var lastHttpStatus: Int? = null
    private var lastIssue: RangeReadIssue? = null
    private var lastRangeMismatch: ContentRangeMismatch? = null
    private var unknownTotalResponses = 0
    private var cacheStart = -1L
    private var cache = ByteArray(0)

    init {
        val root = URI(libraryRoot)
        val testHost = allowLoopbackHttpForTests && root.scheme == "http" &&
            root.host in setOf("localhost", "127.0.0.1", "::1")
        require((root.scheme == "https" && root.host == "webdav.cloud.mail.ru") || testHost) {
            "Untrusted WebDAV host"
        }
        require(root.rawPath == "/AllMusic/" && root.userInfo == null &&
            root.query == null && root.fragment == null) { "Invalid library root" }
        require(fileSize > 0L) { "Unknown remote size" }
        require(encodedPath.startsWith("/AllMusic/")) { "Invalid file path" }
        val target = URI(root.scheme + "://" + root.rawAuthority + encodedPath)
        require(target.rawPath == encodedPath && target.query == null && target.fragment == null &&
            target.host == root.host) { "Invalid encoded file" }
        val part = encodedPath.removePrefix("/AllMusic/")
        require(part.isNotEmpty() && '/' !in part && !part.contains("%2f", true) &&
            !part.contains("%5c", true)) { "Only one-level tracks supported" }
        val name = URLDecoder.decode(part.replace("+", "%2B"), "UTF-8")
        require(name.isNotBlank() && name != "." && name != ".." &&
            name.none { it == '/' || it == '\\' || it.code < 32 }) { "Invalid track name" }
        require(email.isNotBlank() && appPassword.isNotBlank())
        val raw = (email.trim() + ":" + appPassword).toByteArray(StandardCharsets.UTF_8)
        auth = try { "Basic " + Base64.getEncoder().encodeToString(raw) }
            finally { raw.fill(0) }
        url = target.toURL()
    }

    @Synchronized
    fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed) throw IOException("Range source closed")
        require(offset >= 0 && length >= 0 && offset <= buffer.size &&
            length <= buffer.size - offset) { "Invalid buffer" }
        if (length == 0) return 0
        if (position < 0L) throw IOException("Invalid position")
        if (position >= fileSize) return -1
        if (cacheStart < 0 || position < cacheStart || position >= cacheStart + cache.size) {
            loadBlock((position / BLOCK) * BLOCK)
        }
        val i = (position - cacheStart).toInt()
        val n = minOf(length, cache.size - i)
        if (n <= 0) throw IOException("Range unavailable")
        System.arraycopy(cache, i, buffer, offset, n)
        return n
    }

    @Synchronized fun stats(): RangeReadStats =
        RangeReadStats(calls, bytesFetched, lastHttpStatus, lastIssue,
            lastRangeMismatch, unknownTotalResponses)

    private fun reject(issue: RangeReadIssue): Nothing {
        lastIssue = issue
        throw IOException("WebDAV Range read failed")
    }

    private fun loadBlock(start: Long) {
        if (calls >= MAX_GETS || bytesFetched >= MAX_BYTES ||
            System.nanoTime() - started > MAX_NS) reject(RangeReadIssue.LIMIT)
        val n = minOf(BLOCK.toLong(), fileSize - start, MAX_BYTES - bytesFetched).toInt()
        if (n <= 0) reject(RangeReadIssue.LIMIT)
        val end = start + n - 1L
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Authorization", auth ?: throw IOException("Source closed"))
            conn.setRequestProperty("Range", "bytes=$start-$end")
            conn.setRequestProperty("Accept-Encoding", "identity")
            if (etagStrong != null) conn.setRequestProperty("If-Range", etagStrong)
            calls++
            lastHttpStatus = conn.responseCode
            if (lastHttpStatus != HttpURLConnection.HTTP_PARTIAL) reject(RangeReadIssue.HTTP_STATUS)
            val range = conn.getHeaderField("Content-Range")?.trim()
                ?: reject(RangeReadIssue.MISSING_CONTENT_RANGE)
            // RFC 9110 permits an unknown complete-length represented by "*".
            // This does NOT relax byte boundaries: we still require exactly the
            // requested start/end and verify the received body has exactly n bytes.
            val match = CONTENT_RANGE.matchEntire(range)
            if (match == null) {
                val kind = when {
                    range.startsWith("bytes=", ignoreCase = true) ->
                        ContentRangeMismatchKind.FORMAT_EQUALS_SEPARATOR
                    !range.startsWith("bytes", ignoreCase = true) ->
                        ContentRangeMismatchKind.FORMAT_UNEXPECTED_UNIT
                    else -> ContentRangeMismatchKind.FORMAT
                }
                lastRangeMismatch = ContentRangeMismatch(kind, start, end, fileSize)
                reject(RangeReadIssue.BAD_CONTENT_RANGE)
            }
            val gotStart = match.groupValues[1].toLongOrNull()
            val gotEnd = match.groupValues[2].toLongOrNull()
            val unknownTotal = match.groupValues[3] == "*"
            val gotTotal = if (unknownTotal) null else match.groupValues[3].toLongOrNull()
            if (gotStart == null || gotEnd == null || (!unknownTotal && gotTotal == null) ||
                gotEnd < gotStart ||
                (!unknownTotal && gotTotal!! <= gotEnd) ||
                gotStart != start || gotEnd != end ||
                (!unknownTotal && gotTotal != fileSize)) {
                val kind = if (gotStart == null || gotEnd == null ||
                    (!unknownTotal && gotTotal == null) ||
                    gotEnd < gotStart ||
                    (!unknownTotal && gotTotal!! <= gotEnd)) {
                    ContentRangeMismatchKind.INVALID_BOUNDS
                } else {
                    val startDiffers = gotStart != start
                    val endDiffers = gotEnd != end
                    val totalDiffers = !unknownTotal && gotTotal != fileSize
                    when (listOf(startDiffers, endDiffers, totalDiffers).count { it }) {
                        1 -> when {
                            startDiffers -> ContentRangeMismatchKind.START
                            endDiffers -> ContentRangeMismatchKind.END
                            else -> ContentRangeMismatchKind.TOTAL
                        }
                        else -> ContentRangeMismatchKind.MULTIPLE
                    }
                }
                lastRangeMismatch = ContentRangeMismatch(kind, start, end, fileSize,
                    gotStart, gotEnd, gotTotal)
                reject(RangeReadIssue.BAD_CONTENT_RANGE)
            }
            if (unknownTotal) unknownTotalResponses++
            val encoding = conn.getHeaderField("Content-Encoding")
            if (encoding != null && !encoding.equals("identity", true)) reject(RangeReadIssue.BAD_ENCODING)
            if (etagStrong != null) {
                val received = conn.getHeaderField("ETag")
                if (received != null && received != etagStrong) reject(RangeReadIssue.CHANGED_ETAG)
            }
            val block = ByteArray(n)
            conn.inputStream.use { input ->
                var total = 0
                while (total < n) {
                    val count = input.read(block, total, n - total)
                    if (count < 0) reject(RangeReadIssue.SHORT_BODY)
                    if (count > 0) {
                        total += count
                        bytesFetched += count
                    }
                }
                if (input.read() != -1) reject(RangeReadIssue.OVERSIZED_BODY)
            }
            cacheStart = start
            cache = block
        } catch (_: IOException) {
            // The HTTP library may include private URL details in its exception.
            if (lastIssue == null) lastIssue = RangeReadIssue.NETWORK
            throw IOException("WebDAV Range read failed")
        } finally {
            conn.disconnect()
        }
    }

    @Synchronized override fun close() {
        closed = true
        auth = null
        cacheStart = -1
        cache = ByteArray(0)
    }

    private companion object {
        const val BLOCK = 64 * 1024
        const val MAX_GETS = 128
        const val MAX_BYTES = 8L * 1024 * 1024
        const val MAX_NS = 40L * 1_000_000_000L
        val CONTENT_RANGE = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)
    }
}
