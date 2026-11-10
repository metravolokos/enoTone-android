package app.enotone.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.enotone.library.EmbeddedArtworkPayloadReader
import app.enotone.library.WebDavRangeReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * One currently-playing track only. Never walks the library or starts automatic
 * background artwork indexing. Encoded paths/account IDs are hashed in cache names.
 */
internal data class PlayingArtworkTarget(
    val id: String,
    val encodedPath: String,
    val remoteSize: Long,
    val remoteEtag: String?,
)

/**
 * 512px private WebP thumbnails; 32MiB best-effort LRU under cacheDir.
 *
 * Separate from Room and the audio cache. Only an explicit play may invoke a
 * WebDAV Range read. No remote path, credentials or ETag are written as text.
 */
internal class ArtworkPreviewRepository(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "enotone-artwork-v1")
    // Bounded across mini-player and visible album thumbnails. No 832-way burst.
    private val imageSlots = Semaphore(2)

    suspend fun cover(
        email: String,
        password: String,
        target: PlayingArtworkTarget,
    ): Bitmap? = imageSlots.withPermit {
        withContext(Dispatchers.IO) {
        if (email.isBlank() || password.isBlank() || target.remoteSize <= 0L) {
            return@withContext null
        }
        val path = target.encodedPath
        val name = runCatching {
            URLDecoder.decode(path.substringAfterLast('/').replace("+", "%2B"), "UTF-8")
        }.getOrNull() ?: return@withContext null
        if (!name.endsWith(".mp3", ignoreCase = true) &&
            !name.endsWith(".flac", ignoreCase = true)) return@withContext null

        // Version and account are part of the key: changed audio invalidates it.
        val key = sha256("v1|" + email + "|" + target.id + "|" + path +
            "|" + target.remoteSize + "|" + target.remoteEtag.orEmpty())
        val file = File(directory, key + ".webp")
        try {
            if (file.isFile && file.length() in 1L..MAX_DISK_FILE_BYTES.toLong()) {
                decode(file.readBytes())?.let { cached ->
                    file.setLastModified(System.currentTimeMillis())
                    return@withContext cached
                }
                file.delete()
            }
            val requestContext = coroutineContext
            requestContext.ensureActive()
            val image = WebDavRangeReader(
                encodedPath = path,
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
            } ?: return@withContext null

            val bitmap = try {
                decode(image)
            } finally {
                image.fill(0)
            } ?: return@withContext null
            coroutineContext.ensureActive()
            store(file, bitmap)
            bitmap
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A missing/malformed image must never stop playback.
            null
        }
        }
    }

    private fun decode(raw: ByteArray): Bitmap? {
        if (raw.isEmpty() || raw.size > MAX_DISK_FILE_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w !in 1..MAX_DIMENSION || h !in 1..MAX_DIMENSION ||
            w.toLong() * h > MAX_PIXELS) return null
        if (bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/webp")) {
            return null
        }
        var sample = 1
        while (maxOf(w, h) / sample > MAX_SIDE * 2) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val original = BitmapFactory.decodeByteArray(raw, 0, raw.size, options) ?: return null
        val larger = maxOf(original.width, original.height)
        if (larger <= MAX_SIDE) return original
        val outW = maxOf(1, original.width * MAX_SIDE / larger)
        val outH = maxOf(1, original.height * MAX_SIDE / larger)
        val scaled = Bitmap.createScaledBitmap(original, outW, outH, true)
        if (scaled !== original) original.recycle()
        return scaled
    }

    private fun store(file: File, bitmap: Bitmap) {
        if (!directory.exists() && !directory.mkdirs()) return
        var temp: File? = null
        try {
            temp = File.createTempFile("cover-", ".tmp", directory)
            val saved = temp.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
            }
            if (!saved || temp.length() !in 1L..MAX_DISK_FILE_BYTES.toLong()) return
            if (!temp.renameTo(file)) return
            trimCache()
        } catch (_: Exception) {
            // Caching is optional. The already decoded image can still render.
        } finally {
            temp?.delete()
        }
    }

    private fun trimCache() {
        val files = directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".webp") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        var bytes = 0L
        files.forEach { item ->
            bytes += item.length().coerceAtLeast(0L)
            if (bytes > CACHE_LIMIT_BYTES) {
                bytes -= item.length().coerceAtLeast(0L)
                item.delete()
            }
        }
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_SIDE = 512
        const val MAX_DIMENSION = 8192
        const val MAX_PIXELS = 40_000_000L
        const val MAX_DISK_FILE_BYTES = 4 * 1024 * 1024
        const val CACHE_LIMIT_BYTES = 32L * 1024 * 1024
    }
}
