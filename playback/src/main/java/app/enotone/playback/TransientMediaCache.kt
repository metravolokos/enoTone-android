package app.enotone.playback

import android.content.Context
import android.net.Uri
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
 * M5-A deliberately downloads only the *current* selected song.
 *
 * Background CacheWriter is independent of ExoPlayer.playWhenReady, so Pause
 * never cancels full-track caching. Playback uses a READ-ONLY CacheDataSource
 * to avoid taking the writer's hole lock and delaying the first sound; any
 * already committed spans (and later seeks/reopens) are served from disk.
 *
 * The on-device SimpleCache is private, capped, never backed up, and deleted
 * when the service dies. Previous process leftovers are cleared at start.
 */
@OptIn(UnstableApi::class)
class TransientMediaCache(context: Context) : AutoCloseable {
    private val directory = File(context.cacheDir, "enotone-m5-temporary-audio")
    private val cache: SimpleCache
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "enotone-cache-current").apply { isDaemon = true }
    }
    private val generation = AtomicLong(0L)
    private val closed = AtomicBoolean(false)
    private val status = AtomicReference(
        TransientCacheStatus(CachePhase.IDLE, 0L, 0L))
    @Volatile private var activeWriter: CacheWriter? = null

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

    /** Playback reads local spans but does not hold the background writer lock. */
    fun forPlayback(upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)

    fun prefetchCurrent(
        key: String,
        uri: Uri,
        size: Long,
        upstream: DataSource.Factory,
    ) {
        if (closed.get()) return
        // Any current-track transition cancels the previous background task.
        activeWriter?.cancel()
        val ticket = generation.incrementAndGet()
        if (!TransientCachePolicy.canDownload(size, directory.usableSpace)) {
            status.set(TransientCacheStatus(CachePhase.SKIPPED, 0L, size))
            return
        }
        status.set(TransientCacheStatus(CachePhase.DOWNLOADING, 0L, size))
        val writerFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
        val dataSpec = DataSpec.Builder()
            .setUri(uri)
            .setKey(key)
            .setPosition(0L)
            .setLength(size)
            .build()
        val writer = CacheWriter(
            writerFactory.createDataSourceForDownloading(),
            dataSpec,
            null,
            CacheWriter.ProgressListener { _, bytesCached, _ ->
                if (!closed.get() && generation.get() == ticket) {
                    status.set(TransientCacheStatus(
                        CachePhase.DOWNLOADING,
                        bytesCached.coerceIn(0L, size), size))
                }
            },
        )
        activeWriter = writer
        worker.execute {
            if (closed.get() || generation.get() != ticket) return@execute
            try {
                writer.cache()
                if (!closed.get() && generation.get() == ticket) {
                    status.set(TransientCacheStatus(CachePhase.COMPLETE, size, size))
                }
            } catch (_: Exception) {
                if (!closed.get() && generation.get() == ticket) {
                    status.set(TransientCacheStatus(CachePhase.FAILED, 0L, size))
                }
            } finally {
                if (generation.get() == ticket) activeWriter = null
            }
        }
    }

    fun stopCurrent() {
        generation.incrementAndGet()
        activeWriter?.cancel()
        activeWriter = null
        status.set(TransientCacheStatus(CachePhase.IDLE, 0L, 0L))
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        stopCurrent()
        worker.shutdownNow()
        // Never block MediaSessionService.onDestroy for a socket timeout.
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
