package app.enotone.library

import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.util.LinkedHashMap
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
    val cacheHits: Int = 0,
    val cacheRevisitHits: Int = 0,
    val cacheEvictions: Int = 0,
)

/**
 * Shared reservation across concurrent metadata readers. A 64-KiB request is
 * reserved before opening the connection. Failed requests retain their
 * reservation: totals remain bounded even when a server fails mid-response.
 * Exact completed byte and GET counters remain on individual readers.
 */
class SharedRangeBudget(private val maxGets: Int, private val maxBytes: Long) {
    init { require(maxGets > 0 && maxBytes > 0L) }
    private var reservedGets = 0
    private var reservedBytes = 0L

    @Synchronized fun tryReserve(bytes: Int): Boolean {
        require(bytes > 0)
        if (reservedGets >= maxGets || bytes.toLong() > maxBytes - reservedBytes) return false
        reservedGets++
        reservedBytes += bytes
        return true
    }

    @Synchronized fun canStartFile(): Boolean =
        reservedGets < maxGets && maxBytes - reservedBytes >= 65536L

    @Synchronized fun reservedRequests(): Int = reservedGets
    @Synchronized fun reservedBytes(): Long = reservedBytes
}

/** For ONE manually requested track; never a playback transport.
 * Four 64 KiB RAM blocks max (256 KiB total), bounded with access-order LRU.
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
    /** Optional tighter per-invocation limits for a multi-file manual pilot. */
    maxRequests: Int = 128,
    maxDownloadedBytes: Long = 8L * 1024 * 1024,
    private val sharedBudget: SharedRangeBudget? = null,
) : Closeable {
    private val url: java.net.URL
    private val allowedRequests: Int
    private val allowedBytes: Long
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
    // Access-order LRU: a reused distant block must not trigger a second HTTP GET.
    // This never holds more than 4 * 64 KiB and is cleared on close.
    private val cacheBlocks = LinkedHashMap<Long, ByteArray>(4, 0.75f, true)
    private var lastReadBlock = -1L
    private var cacheHits = 0
    private var cacheRevisitHits = 0
    private var cacheEvictions = 0

    init {
        require(maxRequests in 1..128 && maxDownloadedBytes in 1L..(8L * 1024 * 1024)) {
            "Invalid per-file network budget"
        }
        allowedRequests = maxRequests
        allowedBytes = maxDownloadedBytes
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
        val blockStart = (position / BLOCK) * BLOCK
        val block = cacheBlocks[blockStart]?.also {
            cacheHits++
            if (lastReadBlock != blockStart) cacheRevisitHits++
        } ?: loadBlock(blockStart)
        lastReadBlock = blockStart
        val i = (position - blockStart).toInt()
        val n = minOf(length, block.size - i)
        if (n <= 0) throw IOException("Range unavailable")
        System.arraycopy(block, i, buffer, offset, n)
        return n
    }

    @Synchronized fun stats(): RangeReadStats =
        RangeReadStats(calls, bytesFetched, lastHttpStatus, lastIssue,
            lastRangeMismatch, unknownTotalResponses, cacheHits, cacheRevisitHits, cacheEvictions)

    private fun reject(issue: RangeReadIssue): Nothing {
        lastIssue = issue
        throw IOException("WebDAV Range read failed")
    }

    private fun loadBlock(start: Long): ByteArray {
        if (calls >= allowedRequests || bytesFetched >= allowedBytes ||
            System.nanoTime() - started > MAX_NS) reject(RangeReadIssue.LIMIT)
        val n = minOf(BLOCK.toLong(), fileSize - start, allowedBytes - bytesFetched).toInt()
        if (n <= 0) reject(RangeReadIssue.LIMIT)
        // Global cap applies to all parallel tracks and all retries.
        if (sharedBudget != null && !sharedBudget.tryReserve(n)) reject(RangeReadIssue.LIMIT)
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
            if (cacheBlocks.size >= MAX_CACHED_BLOCKS) {
                val eldest = cacheBlocks.entries.iterator()
                if (eldest.hasNext()) {
                    val entry = eldest.next()
                    // Wipe evicted audio bytes instead of leaving them in discarded arrays.
                    entry.value.fill(0)
                    eldest.remove()
                    cacheEvictions++
                }
            }
            cacheBlocks[start] = block
            return block
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
        cacheBlocks.values.forEach { it.fill(0) }
        cacheBlocks.clear()
        lastReadBlock = -1L
    }

    private companion object {
        const val BLOCK = 64 * 1024
        const val MAX_CACHED_BLOCKS = 4
         const val MAX_NS = 40L * 1_000_000_000L
        // Cloud.Mail.ru sends non-standard "bytes=..." in Content-Range.
        // Accept only that observed separator alongside the RFC "bytes ..." form.
        // matchEntire() and the checks above still enforce every numeric boundary.
        val CONTENT_RANGE = Regex("""bytes(?:\s+|=)(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)
    }
}
