package app.enotone.android

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.test.core.app.ApplicationProvider
import app.enotone.playback.EnoTonePlaybackService
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.PreviewAudioPlayback
import app.enotone.playback.QueueEditStatus
import app.enotone.playback.QueueStateFileStore
import app.enotone.playback.QueueTrack
import app.enotone.playback.StagedPlaylistPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.FileNotFoundException
import java.io.InterruptedIOException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Staged Shuffle navigation with a local blocking source, never real WebDAV. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueueShuffleStagingServiceTest {
    private fun id(n: Int) = n.toString(16).padStart(64, '0')
    private fun track(n: Int) =
        QueueTrack(id(n), "Fixture $n", "/AllMusic/$n.mp3", 1_024L, null)

    @Test fun stagedShuffleNextAndPreviousFollowLogicalOrderBeforeTimelineCompletes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 100).map(::track)
        val anchoredSeedOrder = IntArray(100) {
            when {
                it == 0 -> 0
                it <= 4 -> 5 - it
                else -> 104 - it
            }
        }
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(0),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = true,
            shuffledIndices = anchoredSeedOrder,
            sourceKey = id(99),
            savedAtEpochMs = 1L,
            queueRevision = UUID.randomUUID().toString(),
        )
        val source = BlockingDataSourceFactory()
        BufferingService.dataSourceFactory = source
        val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    queue, initial, false, false))
                assertTrue(service.player.mediaItemCount < queue.size)

                service.playNext()
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
                assertEquals(id(4), service.player.currentMediaItem?.mediaId)
                assertEquals(1, service.queueSnapshot?.currentIndex)
                assertTrue(service.player.mediaItemCount < queue.size)

                service.playPrevious()
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
                assertEquals(id(0), service.player.currentMediaItem?.mediaId)
                assertEquals(0, service.queueSnapshot?.currentIndex)
                assertFalse(service.hasPrevious)
            }
        } finally {
            source.release.countDown()
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            BufferingService.dataSourceFactory = null
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun stagedShuffleSelectionSeeksLoadedTargetBeforeFullTimeline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 100).map(::track)
        val anchoredOrder = IntArray(100) { if (it == 0) 0 else 100 - it }
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(0),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = true,
            shuffledIndices = anchoredOrder,
            sourceKey = id(99),
            savedAtEpochMs = 1L,
            queueRevision = UUID.randomUUID().toString(),
        )
        val source = BlockingDataSourceFactory()
        BufferingService.dataSourceFactory = source
        val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    queue, initial, false, false))
                assertTrue(service.skipToQueuePosition(4))
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))

                assertEquals(id(96), service.player.currentMediaItem?.mediaId)
                assertEquals(4, service.queueSnapshot?.currentIndex)
                assertTrue(service.player.mediaItemCount < queue.size)
                assertTrue("target should not wait for the whole after-tail",
                    service.player.mediaItemCount <= 5 + StagedPlaylistPlan.CHUNK_SIZE)
            }
        } finally {
            source.release.countDown()
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            BufferingService.dataSourceFactory = null
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun stagedShuffleSelectionLoadsPhysicalHistoryTargetWithoutWholeTail() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 200).map(::track)
        val logicalOrder = mutableListOf(100, 1).apply {
            addAll((0 until queue.size).filter { it != 100 && it != 1 })
        }
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(100),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = true,
            shuffledIndices = logicalOrder.toIntArray(),
            sourceKey = id(99),
            savedAtEpochMs = 1L,
            queueRevision = UUID.randomUUID().toString(),
        )
        val source = BlockingDataSourceFactory()
        BufferingService.dataSourceFactory = source
        val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    queue, initial, false, false))
                assertTrue(service.skipToQueuePosition(1))
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))

                assertEquals(id(1), service.player.currentMediaItem?.mediaId)
                assertEquals(1, service.queueSnapshot?.currentIndex)
                assertTrue(service.player.mediaItemCount < queue.size)
                assertTrue("history target should load as a bounded chunk",
                    service.player.mediaItemCount <= 5 + StagedPlaylistPlan.CHUNK_SIZE)
            }
        } finally {
            source.release.countDown()
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            BufferingService.dataSourceFactory = null
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun stagedShuffleFillCompletesWithoutDuplicatesWhilePaused() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 100).map(::track)
        val anchoredOrder = IntArray(100) { if (it == 0) 0 else 100 - it }
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(0),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = true,
            shuffledIndices = anchoredOrder,
            sourceKey = id(99),
            savedAtEpochMs = 1L,
            queueRevision = UUID.randomUUID().toString(),
        )
        val controller = onMain { Robolectric.buildService(EnoTonePlaybackService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    queue, initial, false, false))
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))

                assertEquals(queue.size, service.player.mediaItemCount)
                assertEquals(id(0), service.player.currentMediaItem?.mediaId)
                assertEquals(queue.size, (0 until service.player.mediaItemCount).map {
                    service.player.getMediaItemAt(it).mediaId
                }.toSet().size)
            }
        } finally {
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun previousAtFirstCancelsPendingForwardSeek() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 100).map(::track)
        val anchoredOrder = IntArray(100) { if (it == 0) 0 else 100 - it }
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(0),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = true,
            shuffledIndices = anchoredOrder,
            sourceKey = id(99),
            savedAtEpochMs = 1L,
            queueRevision = UUID.randomUUID().toString(),
        )
        val source = BlockingDataSourceFactory()
        BufferingService.dataSourceFactory = source
        val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    queue, initial, false, false))
                assertTrue(service.skipToQueuePosition(1))
                assertFalse(service.hasPrevious)

                service.playPrevious()
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
                assertEquals(id(0), service.player.currentMediaItem?.mediaId)
                assertEquals(0, service.queueSnapshot?.currentIndex)
            }
        } finally {
            source.release.countDown()
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            BufferingService.dataSourceFactory = null
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun repeatAllWrapsLogicalPreviousAndNextAtQueueBoundaries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 3).map(::track)
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(0),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = true,
            shuffledIndices = intArrayOf(0, 1, 2),
            sourceKey = id(99),
            savedAtEpochMs = 1L,
            queueRevision = UUID.randomUUID().toString(),
        )
        val source = BlockingDataSourceFactory()
        BufferingService.dataSourceFactory = source
        val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    queue, initial, false, false))
                assertFalse(service.hasPrevious)

                service.player.repeatMode = Player.REPEAT_MODE_ALL
                assertTrue(service.hasPrevious)
                assertTrue(service.hasNext)
                service.playPrevious()
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
                assertEquals(id(2), service.player.currentMediaItem?.mediaId)
                assertEquals(2, service.queueSnapshot?.currentIndex)

                service.playNext()
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
                assertEquals(id(0), service.player.currentMediaItem?.mediaId)
                assertEquals(0, service.queueSnapshot?.currentIndex)
            }
        } finally {
            source.release.countDown()
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            BufferingService.dataSourceFactory = null
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun queueEditAfterTargetAwareLoadFinishesSparseTimelineWithoutDuplicates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 100).map(::track)
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(0),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = true,
            shuffledIndices = IntArray(100) { if (it == 0) 0 else 100 - it },
            sourceKey = id(99),
            savedAtEpochMs = 1L,
            queueRevision = UUID.randomUUID().toString(),
        )
        val source = BlockingDataSourceFactory()
        BufferingService.dataSourceFactory = source
        val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    queue, initial, false, false))
                assertTrue(service.skipToQueuePosition(1))
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
                assertEquals(id(99), service.player.currentMediaItem?.mediaId)
                assertTrue(service.player.mediaItemCount < queue.size)

                assertEquals(QueueEditStatus.APPLIED, service.append(listOf(track(100))).status)
                assertEquals(101, service.player.mediaItemCount)
                val loadedIds = (0 until service.player.mediaItemCount).map {
                    service.player.getMediaItemAt(it).mediaId
                }
                assertEquals(101, loadedIds.toSet().size)
                val snapshot = requireNotNull(service.queueSnapshot)
                assertEquals(101, snapshot.trackIds.size)
                assertEquals(id(99), snapshot.trackIds[snapshot.currentIndex])
                assertTrue(id(0) in snapshot.trackIds)
                assertEquals(id(100), snapshot.trackIds.last())
            }
        } finally {
            source.release.countDown()
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            BufferingService.dataSourceFactory = null
            QueueStateFileStore(context).clear()
        }
    }

    @OptIn(UnstableApi::class)
    @Test
    fun playAfterErrorDuringStagedShuffleUsesLogicalNext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 100).map(::track)
        val anchoredSeedOrder = IntArray(100) {
            when {
                it == 0 -> 0
                it <= 4 -> 5 - it
                else -> 104 - it
            }
        }
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(0),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = true,
            shuffledIndices = anchoredSeedOrder,
            sourceKey = id(99),
            savedAtEpochMs = 1L,
            queueRevision = UUID.randomUUID().toString(),
        )
        val source = ErrorThenBlockingDataSourceFactory()
        BufferingService.dataSourceFactory = source
        val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    queue, initial, false, false))
                assertTrue(service.player.mediaItemCount < queue.size)
                // Make the native next deterministically different from the logical one.
                (service.player as ExoPlayer).setShuffleOrder(
                    ShuffleOrder.DefaultShuffleOrder(IntArray(service.player.mediaItemCount) { it }, 0L),
                )
                service.resume()
            }
            assertTrue("initial read should fail", awaitPlayerError(service))

            source.allowOpen = true
            onMain { service.resume() }
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

            onMain {
                assertEquals(id(4), service.player.currentMediaItem?.mediaId)
                assertEquals(1, service.queueSnapshot?.currentIndex)
                assertEquals(Player.STATE_BUFFERING, service.player.playbackState)
                assertTrue(service.player.playWhenReady)
            }
        } finally {
            source.release.countDown()
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            BufferingService.dataSourceFactory = null
            QueueStateFileStore(context).clear()
        }
    }

    @OptIn(UnstableApi::class)
    @Test
    fun playAfterErrorLoadsUnloadedLogicalNextBeforeFullTimeline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 100).map(::track)
        val logicalOrder = mutableListOf(0, 99).apply {
            addAll((1 until 99).toList())
        }
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(0),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = true,
            shuffledIndices = logicalOrder.toIntArray(),
            sourceKey = id(99),
            savedAtEpochMs = 1L,
            queueRevision = UUID.randomUUID().toString(),
        )
        val source = ErrorThenBlockingDataSourceFactory()
        BufferingService.dataSourceFactory = source
        val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    queue, initial, false, false))
                assertTrue(service.player.mediaItemCount < queue.size)
                (service.player as ExoPlayer).setShuffleOrder(
                    ShuffleOrder.DefaultShuffleOrder(IntArray(service.player.mediaItemCount) { it }, 0L),
                )
                service.resume()
            }
            assertTrue("initial read should fail", awaitPlayerError(service))

            source.allowOpen = true
            onMain { service.resume() }
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

            onMain {
                assertEquals(id(99), service.player.currentMediaItem?.mediaId)
                assertEquals(1, service.queueSnapshot?.currentIndex)
                assertTrue(service.player.mediaItemCount < queue.size)
                assertTrue("logical next should load as a bounded chunk",
                    service.player.mediaItemCount <= 3 + StagedPlaylistPlan.CHUNK_SIZE)
                assertEquals(Player.STATE_BUFFERING, service.player.playbackState)
                assertTrue(service.player.playWhenReady)
            }
        } finally {
            source.release.countDown()
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            BufferingService.dataSourceFactory = null
            QueueStateFileStore(context).clear()
        }
    }

    @OptIn(UnstableApi::class)
    @Test
    fun playAfterErrorOnLastLogicalTrackRestartsCurrent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 100).map(::track)
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(0),
            positionMs = 14_000L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = true,
            shuffledIndices = IntArray(100) { (it + 1) % 100 },
            sourceKey = id(99),
            savedAtEpochMs = 1L,
            queueRevision = UUID.randomUUID().toString(),
        )
        val source = ErrorThenBlockingDataSourceFactory()
        BufferingService.dataSourceFactory = source
        val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    queue, initial, false, false))
                assertTrue(service.player.mediaItemCount < queue.size)
                (service.player as ExoPlayer).setShuffleOrder(
                    ShuffleOrder.DefaultShuffleOrder(IntArray(service.player.mediaItemCount) { it }, 0L),
                )
                service.resume()
            }
            assertTrue("initial read should fail", awaitPlayerError(service))

            source.allowOpen = true
            onMain { service.resume() }
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

            onMain {
                assertEquals(id(0), service.player.currentMediaItem?.mediaId)
                assertEquals(99, service.queueSnapshot?.currentIndex)
                assertEquals(0L, service.player.currentPosition)
                assertEquals(Player.STATE_BUFFERING, service.player.playbackState)
                assertTrue(service.player.playWhenReady)
            }
        } finally {
            source.release.countDown()
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            BufferingService.dataSourceFactory = null
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun repeatAllSingleTrackKeepsNextPreviousAvailableAndRestartsCurrent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = listOf(track(0))
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(0),
            positionMs = 14_000L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = false,
            shuffledIndices = null,
            sourceKey = id(99),
            savedAtEpochMs = 1L,
            queueRevision = UUID.randomUUID().toString(),
        )
        val source = BlockingDataSourceFactory()
        BufferingService.dataSourceFactory = source
        val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    queue, initial, false, false))
                assertEquals(14_000L, service.player.currentPosition)
                service.player.repeatMode = Player.REPEAT_MODE_ALL
                assertTrue(service.hasNext)
                assertTrue(service.hasPrevious)

                service.playNext()
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
                assertEquals(0L, service.player.currentPosition)

                service.player.seekTo(7_000L)
                assertEquals(7_000L, service.player.currentPosition)
                service.playPrevious()
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
                assertEquals(0L, service.player.currentPosition)
            }
        } finally {
            source.release.countDown()
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            BufferingService.dataSourceFactory = null
            QueueStateFileStore(context).clear()
        }
    }

    private fun awaitPlayerError(service: EnoTonePlaybackService): Boolean {
        repeat(160) {
            val error = onMain {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                service.player.playerError
            }
            if (error != null) return true
            Thread.sleep(25L)
        }
        return false
    }

    private fun <T> onMain(block: () -> T): T {
        var outcome: Result<T>? = null
        Shadows.shadowOf(Looper.getMainLooper()).runPaused {
            outcome = runCatching(block)
        }
        return requireNotNull(outcome).getOrThrow()
    }

    private class ErrorThenBlockingDataSourceFactory : DataSource.Factory {
        @Volatile var allowOpen = false
        val release = CountDownLatch(1)

        override fun createDataSource(): DataSource = object : DataSource {
            override fun addTransferListener(transferListener: TransferListener) = Unit
            override fun open(dataSpec: DataSpec): Long {
                if (!allowOpen) throw FileNotFoundException("fixture unavailable")
                try {
                    release.await(20, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("fixture source interrupted")
                }
                return dataSpec.length
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT
            override fun getUri(): Uri? = null
            override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
            override fun close() = Unit
        }
    }

    private class BlockingDataSourceFactory : DataSource.Factory {
        val release = CountDownLatch(1)

        override fun createDataSource(): DataSource = object : DataSource {
            override fun addTransferListener(transferListener: TransferListener) = Unit
            override fun open(dataSpec: DataSpec): Long {
                try {
                    release.await(20, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("fixture source interrupted")
                }
                return 1_024L
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT
            override fun getUri(): Uri? = null
            override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
            override fun close() = Unit
        }
    }

    private class BufferingService : EnoTonePlaybackService() {
        @OptIn(UnstableApi::class)
        override fun createPlayback(): PreviewAudioPlayback =
            PreviewAudioPlayback(this, requireNotNull(dataSourceFactory))

        companion object {
            @Volatile var dataSourceFactory: DataSource.Factory? = null
        }
    }
}
