package app.enotone.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.enotone.library.EmbeddedArtworkPayloadReader
import app.enotone.library.WebDavRangeReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * The source/version identity of an embedded picture, never an album-wide
 * assumption: individual tracks in one album may contain different artwork.
 * refreshEpoch only triggers UI recomposition after a confirmed manual refresh;
 * it is deliberately NOT part of the persistent cache key.
 */
internal data class PlayingArtworkTarget(
    val id: String,
    val encodedPath: String,
    val remoteSize: Long,
    val remoteEtag: String?,
    val refreshEpoch: Int = 0,
)

/**
 * One high-quality compressed master per distinct picture (up to 1320 px).
 * Each source's hashed .ref points to a SHA-256-addressed WebP master. Small
 * views decode/scale from the master, never save extra thumbnail disk files.
 *
 * Both ref names and master names contain only hashes, not account IDs, paths,
 * tags, credentials or ETags. No Room reads, album-wide prefetch or indexing.
 */
internal class ArtworkPreviewRepository(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "enotone-artwork-v2")

    /**
     * Non-blocking lookup for Compose's FIRST frame when a lazy row or album
     * tile is recreated. Never touches disk or starts HTTP. If a different
     * display size is already in RAM, Compose may scale it visually while the
     * requested quality is decoded in the background.
     */
    fun peek(email: String, target: PlayingArtworkTarget, requestedSide: Int): Bitmap? {
        if (email.isBlank() || target.remoteSize <= 0L) return null
        val key = sourceKey(email, target)
        val side = requestedSide.coerceIn(1, MAX_SIDE)
        memoryCache.get("$key|$side")?.let { return it }
        return VIEW_SIDES.asSequence().filter { it > side }
            .mapNotNull { memoryCache.get("$key|$it") }.firstOrNull()
            ?: VIEW_SIDES.asSequence().filter { it < side }
                .sortedDescending()
                .mapNotNull { memoryCache.get("$key|$it") }.firstOrNull()
    }

    /** Reuse a resident larger bitmap instead of opening the WebP master. */
    private fun cachedSizedCover(key: String, side: Int): Bitmap? {
        val memoryKey = "$key|$side"
        memoryCache.get(memoryKey)?.let { return it }
        val larger = VIEW_SIDES.asSequence().filter { it > side }
            .mapNotNull { memoryCache.get("$key|$it") }.firstOrNull()
            ?: return null
        val scaled = scaleToSide(larger, side)
        memoryCache.put(memoryKey, scaled)
        return scaled
    }

    suspend fun cover(
        email: String,
        password: String,
        target: PlayingArtworkTarget,
        requestedSide: Int = 512,
    ): Bitmap? {
        if (email.isBlank() || password.isBlank() || target.remoteSize <= 0L) return null
        val side = requestedSide.coerceIn(1, MAX_SIDE)
        val path = target.encodedPath
        val name = runCatching {
            URLDecoder.decode(path.substringAfterLast('/').replace("+", "%2B"), "UTF-8")
        }.getOrNull() ?: return null
        if (!name.endsWith(".mp3", ignoreCase = true) &&
            !name.endsWith(".flac", ignoreCase = true)) return null

        val key = sourceKey(email, target)
        val memoryKey = "$key|$side"
        cachedSizedCover(key, side)?.let { return it }

        // Shared across repository instances, current playback and visible
        // LazyColumn/Grid cells. Two image network/IO tasks at most.
        return imageSlots.withPermit {
            val lock = sourceLocks[key.substring(0, 2).toInt(16) % sourceLocks.size]
            lock.lock()
            try {
                cachedSizedCover(key, side) ?: withContext(Dispatchers.IO) {
                    loadOrFetch(key, memoryKey, side, email, password, target, name)
                }
            } finally {
                lock.unlock()
            }
        }
    }

    /**
     * An explicit reindex may change an embedded picture even if the server
     * incorrectly retains the same ETag and size. Forget only those source
     * references; shared image blobs survive for any other source that uses them.
     */
    suspend fun invalidate(email: String, targets: Collection<PlayingArtworkTarget>) {
        if (email.isBlank() || targets.isEmpty()) return
        withContext(Dispatchers.IO) {
            for (target in targets) {
                val key = sourceKey(email, target)
                val lock = sourceLocks[key.substring(0, 2).toInt(16) % sourceLocks.size]
                lock.lock()
                try {
                    referenceFile(key).delete()
                } finally {
                    lock.unlock()
                }
            }
            // Memory entries may have been derived from any of these references.
            // The small bounded RAM LRU is cheap to refill from intact disk masters.
            memoryCache.evictAll()
        }
    }

    private suspend fun loadOrFetch(
        key: String,
        memoryKey: String,
        side: Int,
        email: String,
        password: String,
        target: PlayingArtworkTarget,
        name: String,
    ): Bitmap? {
        try {
            val reference = referenceFile(key)
            val digest = if (reference.isFile && reference.length() == 64L) {
                reference.readText(Charsets.US_ASCII).takeIf { HEX.matches(it) }
            } else null
            if (digest != null) {
                val diskFile = masterFile(digest)
                if (diskFile.isFile && diskFile.length() in 1L..MAX_DISK_FILE_BYTES.toLong()) {
                    decodeFile(diskFile, side)?.let { cached ->
                        diskFile.setLastModified(System.currentTimeMillis())
                        memoryCache.put(memoryKey, cached)
                        return cached
                    }
                    diskFile.delete()
                }
            }
            // Bad, missing or evicted master: a source-local reference is not
            // enough to reconstruct content. Refetch this source lazily.
            reference.delete()
            val requestContext = coroutineContext
            requestContext.ensureActive()
            val raw = WebDavRangeReader(
                encodedPath = target.encodedPath,
                fileSize = target.remoteSize,
                email = email,
                appPassword = password,
                etag = target.remoteEtag,
                maxRequests = 128,
                maxDownloadedBytes = 8L * 1024 * 1024,
            ).use { reader ->
                EmbeddedArtworkPayloadReader().read(name, target.remoteSize) {
                    position, buffer, offset, length ->
                    requestContext.ensureActive()
                    reader.readAt(position, buffer, offset, length)
                }
            } ?: return null

            val master = try {
                decode(raw, MAX_SIDE)
            } finally {
                raw.fill(0)
            } ?: return null
            coroutineContext.ensureActive()
            try {
                // Store the quality master BEFORE making a small UI bitmap.
                // Other tracks with identical decoded artwork share its blob.
                storeMaster(key, master)
                val shown = scaleToSide(master, side)
                memoryCache.put(memoryKey, shown)
                return shown
            } finally {
                // The master bitmap may itself be the returned image when
                // requestedSide >= its natural dimensions; never recycle it.
                // Otherwise let GC own it after local downscaling.
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Corrupt/missing images must never prevent music playback.
            return null
        }
    }

    private fun decodeFile(file: File, requestedSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return decodeValidated(bounds, requestedSide) { options ->
            BitmapFactory.decodeFile(file.absolutePath, options)
        }
    }

    private fun decode(raw: ByteArray, requestedSide: Int): Bitmap? {
        if (raw.isEmpty() || raw.size > MAX_DISK_FILE_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        return decodeValidated(bounds, requestedSide) { options ->
            BitmapFactory.decodeByteArray(raw, 0, raw.size, options)
        }
    }

    private fun decodeValidated(
        bounds: BitmapFactory.Options,
        requestedSide: Int,
        read: (BitmapFactory.Options) -> Bitmap?,
    ): Bitmap? {
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w !in 1..MAX_DIMENSION || h !in 1..MAX_DIMENSION ||
            w.toLong() * h > MAX_PIXELS) return null
        if (bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/webp")) return null
        val side = requestedSide.coerceIn(1, MAX_SIDE)
        var sample = 1
        while (maxOf(w, h) / sample > side * 2) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = read(options) ?: return null
        return scaleToSide(decoded, side)
    }

    private fun scaleToSide(bitmap: Bitmap, requestedSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= requestedSide) return bitmap
        val outWidth = maxOf(1, bitmap.width * requestedSide / longest)
        val outHeight = maxOf(1, bitmap.height * requestedSide / longest)
        return Bitmap.createScaledBitmap(bitmap, outWidth, outHeight, true)
    }

    private fun storeMaster(key: String, bitmap: Bitmap) {
        if (!directory.exists() && !directory.mkdirs()) return
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.WEBP, 88, output)) return
        val encoded = output.toByteArray()
        if (encoded.size !in 1..MAX_DISK_FILE_BYTES) return
        try {
            val digest = sha256(encoded)
            val master = masterFile(digest)
            if (!master.isFile && !atomicWrite(master, encoded)) return
            // A reference is written only after its master exists.
            if (master.isFile) {
                atomicWrite(referenceFile(key), digest.toByteArray(Charsets.US_ASCII))
                trimCache()
            }
        } finally {
            encoded.fill(0)
        }
    }

    private fun atomicWrite(destination: File, bytes: ByteArray): Boolean {
        if (!directory.exists() && !directory.mkdirs()) return false
        var temp: File? = null
        return try {
            temp = File.createTempFile("art-", ".tmp", directory)
            temp.outputStream().use { it.write(bytes) }
            temp.renameTo(destination)
        } catch (_: Exception) {
            false
        } finally {
            temp?.delete()
        }
    }

    private fun trimCache() {
        val files = directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith("art-") && it.name.endsWith(".webp") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        var bytes = 0L
        files.forEach { file ->
            bytes += file.length().coerceAtLeast(0L)
            if (bytes > CACHE_LIMIT_BYTES) {
                bytes -= file.length().coerceAtLeast(0L)
                file.delete()
            }
        }
        // Tiny source refs to evicted blobs are removed on demand.
    }

    private fun referenceFile(sourceHash: String) = File(directory, "ref-$sourceHash.txt")
    private fun masterFile(contentHash: String) = File(directory, "art-$contentHash.webp")

    private fun sourceKey(email: String, target: PlayingArtworkTarget) =
        sha256(("v2|" + email + "|" + target.id + "|" + target.encodedPath +
            "|" + target.remoteSize + "|" + target.remoteEtag.orEmpty())
            .toByteArray(Charsets.UTF_8))

    private fun sha256(input: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(input)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_SIDE = 1320
        const val MAX_DIMENSION = 8192
        const val MAX_PIXELS = 40_000_000L
        const val MAX_DISK_FILE_BYTES = 4 * 1024 * 1024
        // Artwork masters are larger than album tiles. 64 MiB could evict
        // previously viewed albums during ordinary browsing, forcing WebDAV
        // rereads. Still a bounded Android-managed cache (not permanent data).
        const val CACHE_LIMIT_BYTES = 192L * 1024 * 1024
        private val VIEW_SIDES = intArrayOf(192, 384, 512, 1320)
        private val HEX = Regex("[0-9a-f]{64}")
        private val imageSlots = Semaphore(2)
        private val sourceLocks = Array(32) { Mutex() }
        // RAM only, different requested sizes; evictions never write variants.
        private val memoryCache = object : LruCache<String, Bitmap>(12 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                maxOf(1, value.byteCount / 1024)
        }
    }
}
