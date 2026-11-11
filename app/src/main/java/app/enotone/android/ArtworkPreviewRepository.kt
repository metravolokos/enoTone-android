package app.enotone.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.enotone.library.EmbeddedArtworkPayloadReader
import app.enotone.library.ArtworkLookupCode
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
import java.util.Locale
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
 * Permanent content-addressed album-cover storage (master up to 1200px).
 * No fixed aggregate size limit. Storage is app-private filesDir, NOT
 * Android's evictable cacheDir; only explicit user action can clear it.
 * Source refs and album refs are hashed; image blobs share SHA-256 filenames.
 * Old temporary v2 entries migrate lazily without another WebDAV download.
 * Small UI variants exist only in bounded RAM, never as duplicate disk files.
 */
internal class ArtworkPreviewRepository(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "enotone-artwork-v2")
    private val legacyDirectory = File(context.applicationContext.cacheDir, "enotone-artwork-v2")

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
     * Resolve an already verified source from the disk cache WITHOUT opening
     * WebDAV. Used for a transient album preview on another library page.
     * A cache miss is just a miss: do not create source refs or guess artwork.
     */
    suspend fun cachedCover(
        email: String,
        target: PlayingArtworkTarget,
        requestedSide: Int = 192,
    ): Bitmap? {
        if (email.isBlank() || target.remoteSize <= 0L) return null
        val side = requestedSide.coerceIn(1, MAX_SIDE)
        val key = sourceKey(email, target)
        cachedSizedCover(key, side)?.let { return it }
        return imageSlots.withPermit {
            val lock = sourceLocks[key.substring(0, 2).toInt(16) % sourceLocks.size]
            lock.lock()
            try {
                cachedSizedCover(key, side) ?: withContext(Dispatchers.IO) {
                    migrateLegacy(key)
                    val reference = referenceFile(key)
                    val digest = if (reference.isFile && reference.length() == 64L) {
                        reference.readText(Charsets.US_ASCII).takeIf { HEX.matches(it) }
                    } else null
                    if (digest == null) null else {
                        val file = masterFile(digest)
                        if (file.isFile &&
                            file.length() in 1L..MAX_DISK_FILE_BYTES.toLong()) {
                            decodeFile(file, side)?.also {
                                memoryCache.put("$key|$side", it)
                                file.setLastModified(System.currentTimeMillis())
                            }
                        } else null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } finally {
                lock.unlock()
            }
        }
    }

    /** A stored album record includes its CURRENT candidate versions. */
    private fun albumKey(email: String, group: AlbumArtworkGroup): String =
        sha256(("album-v1|" + email.trim().lowercase(Locale.ROOT) + "|" +
            group.key).toByteArray(Charsets.UTF_8))

    private fun groupSignature(email: String, group: AlbumArtworkGroup): String =
        sha256(group.targets.joinToString("|") { sourceKey(email, it) }
            .toByteArray(Charsets.US_ASCII))

    private fun albumReference(key: String) = File(directory, "album-$key.txt")

    private fun selectedSource(
        email: String, group: AlbumArtworkGroup,
    ): String? {
        val ref = albumReference(albumKey(email, group))
        if (!ref.isFile || ref.length() != 129L) return null
        val fields = ref.readText(Charsets.US_ASCII).split(":")
        return if (fields.size == 2 &&
            fields[0] == groupSignature(email, group) &&
            HEX.matches(fields[1])) fields[1] else null
    }

    private fun saveAlbumSelection(
        email: String, group: AlbumArtworkGroup, choice: String,
    ) {
        val key = albumKey(email, group)
        atomicWrite(albumReference(key),
            (groupSignature(email, group) + ":" + choice).toByteArray(Charsets.US_ASCII))
    }

    /**
     * Fast first frame. Looks only at RAM, not at the network or storage.
     * Album aliases are visual; verified source references remain individual.
     */
    fun peekAlbum(email: String, group: AlbumArtworkGroup, requestedSide: Int): Bitmap? {
        if (email.isBlank()) return null
        val side = requestedSide.coerceIn(1, MAX_SIDE)
        val key = albumKey(email, group)
        memoryCache.get("album-$key|$side")?.let { return it }
        return group.targets.asSequence().mapNotNull { peek(email, it, side) }.firstOrNull()
    }

    /** A clean local-only cache lookup, never HTTP. */
    suspend fun cachedAlbumCover(
        email: String, group: AlbumArtworkGroup, requestedSide: Int = 192,
    ): Bitmap? {
        if (email.isBlank() || group.targets.isEmpty()) return null
        val side = requestedSide.coerceIn(1, MAX_SIDE)
        val key = albumKey(email, group)
        memoryCache.get("album-$key|$side")?.let { return it }
        val choice = withContext(Dispatchers.IO) { selectedSource(email, group) }
            ?: return null
        if (choice == NO_ART_DIGEST) return null
        val target = group.targets.firstOrNull { sourceKey(email, it) == choice }
            ?: return null
        return cachedCover(email, target, side)?.also {
            memoryCache.put("album-$key|$side", it)
        }
    }

    /**
     * Persisted checkpoint is either one verified source or DEFINITIVE
     * absence across all candidates. Network failures are never complete.
     * Used to resume background indexing without rereading completed albums.
     */
    suspend fun isAlbumIndexed(email: String, group: AlbumArtworkGroup): Boolean =
        withContext(Dispatchers.IO) {
            val choice = selectedSource(email, group) ?: return@withContext false
            if (choice == NO_ART_DIGEST) return@withContext true
            if (group.targets.none { sourceKey(email, it) == choice })
                return@withContext false
            val reference = referenceFile(choice)
            if (!reference.isFile || reference.length() != 64L)
                return@withContext false
            val digest = reference.readText(Charsets.US_ASCII)
            digest != NO_ART_DIGEST && HEX.matches(digest) &&
                masterFile(digest).isFile
        }

    /**
     * All pages and the optional background worker call the SAME loader.
     * For a real album, only the first viable embedded picture is needed.
     * Each missing candidate is checked once; transient errors are retried
     * on a later viewing or app launch, not checkpointed as no-cover.
     */
    suspend fun albumCover(
        email: String,
        password: String,
        group: AlbumArtworkGroup,
        requestedSide: Int = 512,
    ): Bitmap? {
        if (email.isBlank() || password.isBlank() || group.targets.isEmpty()) return null
        val side = requestedSide.coerceIn(1, MAX_SIDE)
        cachedAlbumCover(email, group, side)?.let { return it }
        val albumHash = albumKey(email, group)
        val lock = albumLocks[albumHash.substring(0, 2).toInt(16) % albumLocks.size]
        lock.lock()
        try {
            cachedAlbumCover(email, group, side)?.let { return it }
            val chosen = withContext(Dispatchers.IO) { selectedSource(email, group) }
            if (chosen == NO_ART_DIGEST) return null
            for (target in group.targets) {
                coroutineContext.ensureActive()
                val loaded = cover(email, password, target, side)
                if (loaded != null) {
                    val choice = sourceKey(email, target)
                    withContext(Dispatchers.IO) {
                        if (referenceFile(choice).isFile) {
                            saveAlbumSelection(email, group, choice)
                        }
                    }
                    memoryCache.put("album-$albumHash|$side", loaded)
                    return loaded
                }
                // A genuine no-art file can be skipped. A failed HTTP read,
                // corrupt image, or full storage must not be permanently
                // classified as empty or cause a storm of fallback GETs.
                val missing = withContext(Dispatchers.IO) {
                    val ref = referenceFile(sourceKey(email, target))
                    ref.isFile && ref.length() == 64L &&
                        ref.readText(Charsets.US_ASCII) == NO_ART_DIGEST
                }
                if (!missing) return null
            }
            withContext(Dispatchers.IO) {
                saveAlbumSelection(email, group, NO_ART_DIGEST)
            }
            return null
        } finally {
            lock.unlock()
        }
    }

    /** User-controlled storage usage, no names or remote paths exposed. */
    suspend fun storedBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }

    /** Clear BOTH stores so the legacy temporary v2 cannot repopulate it. */
    suspend fun clearAll() {
        imageSlots.withPermit {
            imageSlots.withPermit {
                withContext(Dispatchers.IO) {
                    directory.deleteRecursively()
                    legacyDirectory.deleteRecursively()
                    memoryCache.evictAll()
                }
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
            // Reindex must also forget negative album checkpoints even when
            // remote ETag and file size did not change.
            directory.listFiles()?.filter {
                it.name.startsWith("album-") && it.name.endsWith(".txt")
            }?.forEach { it.delete() }
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
            migrateLegacy(key)
            val reference = referenceFile(key)
            val digest = if (reference.isFile && reference.length() == 64L) {
                reference.readText(Charsets.US_ASCII).takeIf { HEX.matches(it) }
            } else null
            if (digest == NO_ART_DIGEST) return null
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
            var lookupCode = ArtworkLookupCode.READ_FAILED
            val raw = WebDavRangeReader(
                encodedPath = target.encodedPath,
                fileSize = target.remoteSize,
                email = email,
                appPassword = password,
                etag = target.remoteEtag,
                maxRequests = 128,
                maxDownloadedBytes = 8L * 1024 * 1024,
            ).use { reader ->
                EmbeddedArtworkPayloadReader().read(name, target.remoteSize,
                    onLookup = { lookupCode = it }) {
                    position, buffer, offset, length ->
                    requestContext.ensureActive()
                    reader.readAt(position, buffer, offset, length)
                }
            }
            if (raw == null) {
                if (lookupCode == ArtworkLookupCode.NOT_FOUND) {
                    // Never mistake an HTTP / decoding error for "no image".
                    atomicWrite(reference, NO_ART_DIGEST.toByteArray(Charsets.US_ASCII))
                }
                return null
            }

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
                // No disk LRU: these files are permanent until user clears them.
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

    /**
     * Preserve previous v2 downloads on upgrade. Convert legacy masters to
     * the new 1200px cap, instead of copying 1320px blobs into permanent
     * storage. Runs only while the source lock is held on Dispatchers.IO.
     */
    private fun migrateLegacy(key: String) {
        if (referenceFile(key).isFile) return
        val legacyRef = File(legacyDirectory, "ref-$key.txt")
        if (!legacyRef.isFile || legacyRef.length() != 64L) return
        val digest = legacyRef.readText(Charsets.US_ASCII)
        if (!HEX.matches(digest) || digest == NO_ART_DIGEST) return
        val legacyMaster = File(legacyDirectory, "art-$digest.webp")
        if (!legacyMaster.isFile ||
            legacyMaster.length() !in 1L..MAX_DISK_FILE_BYTES.toLong()) return
        val bitmap = decodeFile(legacyMaster, MAX_SIDE) ?: return
        storeMaster(key, bitmap)
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
        const val MAX_SIDE = 1200
        const val MAX_DIMENSION = 8192
        const val MAX_PIXELS = 40_000_000L
        const val MAX_DISK_FILE_BYTES = 4 * 1024 * 1024
        // No total disk ceiling; available physical storage remains finite.
        private val NO_ART_DIGEST = "0".repeat(64)
        private val VIEW_SIDES = intArrayOf(192, 384, 512, 1320)
        private val HEX = Regex("[0-9a-f]{64}")
        private val imageSlots = Semaphore(2)
        private val sourceLocks = Array(32) { Mutex() }
        private val albumLocks = Array(32) { Mutex() }
        // RAM only, different requested sizes; evictions never write variants.
        private val memoryCache = object : LruCache<String, Bitmap>(24 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                maxOf(1, value.byteCount / 1024)
        }
    }
}
