package app.enotone.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class CachePhase { IDLE, DOWNLOADING, COMPLETE, SKIPPED, FAILED }

data class TransientCacheStatus(
    val phase: CachePhase,
    val cachedBytes: Long,
    val totalBytes: Long,
)

/**
 * M5-B: download the selected current song to 100%, then exactly one upcoming
 * song. One worker serializes the HTTP downloads. Neither job is tied to
 * ExoPlayer.playWhenReady: Pause never stops a started download.
 *
 * Playback uses read-only CacheDataSource to avoid blocking the first audible
 * buffer on a CacheWriter hole lock. Files live only under private cacheDir.
 * A stopped/changed track cancels both jobs and invalidates old callbacks.
 */
@OptIn(UnstableApi::class)
class TransientMediaCache(context: Context) : AutoCloseable {
    private val directory = File(context.cacheDir, "enotone-m5-temporary-audio")
    private val cache: SimpleCache
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "enotone-cache-current-next").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0L)
    private val nextGeneration = AtomicLong(0L)
    private val closed = AtomicBoolean(false)
    private val status = AtomicReference(
        TransientCacheStatus(CachePhase.IDLE, 0L, 0L))
    private val nextStatus = AtomicReference(
        TransientCacheStatus(CachePhase.IDLE, 0L, 0L))
    @Volatile private var activeWriter: CacheWriter? = null
    @Volatile private var activeNextWriter: CacheWriter? = null

    init {
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IllegalStateException("Temporary cache reset failed")
        }
        if (!directory.mkdirs()) {
            throw IllegalStateException("Temporary cache directory unavailable")
        }
        cache = SimpleCache(
            directory,
            LeastRecentlyUsedCacheEvictor(TransientCachePolicy.MAX_DISK_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        )
    }

    fun snapshot(): TransientCacheStatus = status.get()
    fun nextSnapshot(): TransientCacheStatus = nextStatus.get()

    /** Playback may use committed local spans while a writer is still running. */
    fun forPlayback(upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)

    private fun newWriter(
        key: String, uri: Uri, size: Long, upstream: DataSource.Factory,
        onProgress: (Long) -> Unit,
    ): CacheWriter {
        val writerFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
        val dataSpec = DataSpec.Builder()
            .setUri(uri)
            .setKey(key)
            .setPosition(0L)
            .setLength(size)
            .build()
        return CacheWriter(
            writerFactory.createDataSourceForDownloading(),
            dataSpec,
            null,
            CacheWriter.ProgressListener { _, bytesCached, _ ->
                onProgress(bytesCached.coerceIn(0L, size))
            },
        )
    }

    /**
     * Called only after the first playable audio buffer is READY. When COMPLETE,
     * notify the owning player on the main thread so it can resolve the actual
     * next Media3 playlist item (including native shuffle/repeat decisions).
     */
    fun prefetchCurrent(
        key: String,
        uri: Uri,
        size: Long,
        upstream: DataSource.Factory,
        onComplete: (() -> Unit)? = null,
    ) {
        if (closed.get()) return
        stopCurrent()
        val ticket = generation.get()
        if (!TransientCachePolicy.canDownload(size, directory.usableSpace)) {
            status.set(TransientCacheStatus(CachePhase.SKIPPED, 0L, size))
            return
        }
        status.set(TransientCacheStatus(CachePhase.DOWNLOADING, 0L, size))
        val writer = newWriter(key, uri, size, upstream) { bytes ->
            if (!closed.get() && generation.get() == ticket) {
                status.set(TransientCacheStatus(CachePhase.DOWNLOADING, bytes, size))
            }
        }
        activeWriter = writer
        worker.execute {
            if (closed.get() || generation.get() != ticket) return@execute
            var completed = false
            try {
                writer.cache()
                if (!closed.get() && generation.get() == ticket) {
                    status.set(TransientCacheStatus(CachePhase.COMPLETE, size, size))
                    completed = true
                }
            } catch (_: Exception) {
                if (!closed.get() && generation.get() == ticket) {
                    status.set(TransientCacheStatus(CachePhase.FAILED, 0L, size))
                }
            } finally {
                if (activeWriter === writer) activeWriter = null
            }
            if (completed && onComplete != null) {
                mainHandler.post {
                    if (!closed.get() && generation.get() == ticket) onComplete()
                }
            }
        }
    }

    /**
     * Called only after COMPLETE of the current file. A second request cannot
     * start in parallel with the current full download (single-thread worker).
     * Replacing the candidate invalidates the old one without clearing current.
     */
    fun prefetchNext(
        key: String,
        uri: Uri,
        size: Long,
        upstream: DataSource.Factory,
    ) {
        if (closed.get()) return
        cancelNext()
        val currentTicket = generation.get()
        val nextTicket = nextGeneration.get()
        if (!TransientCachePolicy.canDownload(size, directory.usableSpace)) {
            nextStatus.set(TransientCacheStatus(CachePhase.SKIPPED, 0L, size))
            return
        }
        nextStatus.set(TransientCacheStatus(CachePhase.DOWNLOADING, 0L, size))
        worker.execute {
            if (closed.get() || generation.get() != currentTicket ||
                nextGeneration.get() != nextTicket) return@execute
            var writer: CacheWriter? = null
            try {
                writer = newWriter(key, uri, size, upstream) { bytes ->
                    if (!closed.get() && generation.get() == currentTicket &&
                        nextGeneration.get() == nextTicket) {
                        nextStatus.set(TransientCacheStatus(
                            CachePhase.DOWNLOADING, bytes, size))
                    }
                }
                activeNextWriter = writer
                writer.cache()
                if (!closed.get() && generation.get() == currentTicket &&
                    nextGeneration.get() == nextTicket) {
                    nextStatus.set(TransientCacheStatus(CachePhase.COMPLETE, size, size))
                }
            } catch (_: Exception) {
                if (!closed.get() && generation.get() == currentTicket &&
                    nextGeneration.get() == nextTicket) {
                    nextStatus.set(TransientCacheStatus(CachePhase.FAILED, 0L, size))
                }
            } finally {
                if (activeNextWriter === writer) activeNextWriter = null
            }
        }
    }

    fun cancelNext() {
        nextGeneration.incrementAndGet()
        activeNextWriter?.cancel()
        activeNextWriter = null
        nextStatus.set(TransientCacheStatus(CachePhase.IDLE, 0L, 0L))
    }

    fun stopCurrent() {
        generation.incrementAndGet()
        activeWriter?.cancel()
        activeWriter = null
        cancelNext()
        status.set(TransientCacheStatus(CachePhase.IDLE, 0L, 0L))
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        stopCurrent()
        worker.shutdownNow()
        Thread({
            try {
                worker.awaitTermination(30, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                cache.release()
                directory.deleteRecursively()
            }
        }, "enotone-cache-cleanup").apply { isDaemon = true }.start()
    }
}
