package app.enotone.android

import android.app.Application
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.test.core.app.ApplicationProvider
import app.enotone.playback.TransientMediaCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Отмена, пришедшая между проверкой билетов и публикацией writer, не должна запускать загрузку.
 *
 * `prefetchNext` проверяет билеты, затем создаёт writer и только потом публикует его в
 * `activeNextWriter`. `cancelNext()` увеличивает `nextGeneration` и отменяет опубликованный
 * writer, поэтому отмена внутри этого окна заставала поле пустым: отменять было нечего, и
 * `cache()` всё равно стартовал — уже после смены трека или Stop.
 *
 * Окно открывается детерминированно через фабрику upstream: `newWriter` вызывает
 * `createDataSourceForDownloading()`, который создаёт upstream `DataSource` **внутри** окна —
 * после проверки билетов и до публикации. Фабрика здесь и отменяет prefetch.
 *
 * `open()` upstream-источника означает, что загрузка реально началась. Именно этого не должно
 * произойти: сети в тесте нет, поэтому любой `open()` — уже провал (и одновременно доказательство,
 * что счётчик работает, см. второй тест).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefetchCancelDuringWindowTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    /** Источник, считающий попытки открыть поток. Реальной сети не касается. */
    private class CountingSource(
        private val opens: AtomicInteger,
        private val onOpen: () -> Unit = {},
    ) : DataSource {
        override fun open(dataSpec: DataSpec): Long {
            opens.incrementAndGet()
            onOpen()
            error("network must not be reached in this test")
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1
        override fun getUri(): Uri? = null
        override fun close() = Unit
        override fun addTransferListener(transferListener: TransferListener) = Unit
    }

    private class Factory(private val source: () -> DataSource) : DataSource.Factory {
        override fun createDataSource(): DataSource = source()
    }

    /**
     * Waits until [TransientMediaCache.close] released the folder. `close()` releases the
     * `SimpleCache` on a daemon thread and deletes the directory as its last step, so the
     * directory disappearing proves the release finished. Without this a following test
     * method in the same class can construct a second cache over a still-locked folder.
     */
    private fun awaitReleased(cache: TransientMediaCache) {
        cache.close()
        val directory = java.io.File(context.cacheDir, "enotone-m5-temporary-audio")
        val deadline = System.currentTimeMillis() + 5_000
        while (directory.exists() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
    }
    private companion object {
        /**
         * How long to watch for an upload after the worker is released. The worker has no
         * other work queued, so it either reaches `cache()` immediately or never; this window
         * only needs to outlast a scheduling round-trip.
         */
        const val WINDOW_OBSERVATION_MS = 250L
    }

    @Test
    fun cancelInsideTheWindowNeverOpensTheUpstream() {
        val opens = AtomicInteger(0)
        val cache = TransientMediaCache(context)
        val inWindow = CountDownLatch(1)
        val released = CountDownLatch(1)
        try {
            val upstream = Factory {
                // Внутри окна: билеты уже проверены, writer ещё не опубликован.
                if (inWindow.count > 0) {
                    inWindow.countDown()
                    released.await(10, TimeUnit.SECONDS)
                }
                CountingSource(opens)
            }
            cache.prefetchNext("key", Uri.EMPTY, 1024L, upstream)
            assertTrue("prefetch must reach the window", inWindow.await(10, TimeUnit.SECONDS))
            cache.cancelNext()
            released.countDown()
            // Отмена уже случилась, а воркер освобождён. Без повторной проверки он
            // немедленно дойдёт до cache() и откроет поток; с ней — не дойдёт никогда.
            // Проверяем до close(), иначе close() сам отменит writer и замаскирует дефект.
            Thread.sleep(WINDOW_OBSERVATION_MS)
            assertEquals(
                "cancel inside the window must stop cache() before any network open",
                0, opens.get(),
            )
        } finally {
            released.countDown()
            awaitReleased(cache)
        }
    }

    @Test
    fun withoutACancelTheUpstreamIsReached() {
        val opens = AtomicInteger(0)
        val cache = TransientMediaCache(context)
        val finished = CountDownLatch(1)
        try {
            cache.prefetchNext("key", Uri.EMPTY, 1024L, Factory { CountingSource(opens) { finished.countDown() } })
            assertTrue("download attempt must happen", finished.await(10, TimeUnit.SECONDS))
        } finally {
            awaitReleased(cache)
        }

        // Сети нет, поэтому open() бросает и загрузка помечается FAILED — важно, что
        // попытка состоялась: иначе тест выше проходил бы тривиально. Media3 повторяет
        // открытие при ошибке, поэтому проверяется факт попытки, а не точное число.
        assertTrue("upstream must be opened when nothing cancels", opens.get() >= 1)
    }
}
