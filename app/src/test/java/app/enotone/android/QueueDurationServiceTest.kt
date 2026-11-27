package app.enotone.android

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.test.core.app.ApplicationProvider
import app.enotone.playback.EnoTonePlaybackService
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.QueueStateFileStore
import app.enotone.playback.QueueTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Duration memory across real Media3 reprepare and persisted checkpoints. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueueDurationServiceTest {
    private fun id(n: Int) = n.toString(16).padStart(64, '0')
    private fun track(n: Int) =
        QueueTrack(id(n), "Fixture $n", "/AllMusic/$n.mp3", 1_024L, null)

    private fun checkpoint(): PersistedQueueState = PersistedQueueState(
        trackIds = (1..3).map(::id),
        selectedTrackId = id(1),
        positionMs = 14_000L,
        repeatMode = Player.REPEAT_MODE_OFF,
        shuffleEnabled = false,
        shuffledIndices = null,
        sourceKey = id(99),
        savedAtEpochMs = 1L,
        queueRevision = UUID.randomUUID().toString(),
    )

    @Test fun restoredDurationStaysScopedToItsTrackWhileMedia3DurationIsUnset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val initial = checkpoint().copy(durationMs = 228_000L)
        val controller = onMain { Robolectric.buildService(EnoTonePlaybackService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    (1..3).map(::track), initial, false, false))
                assertEquals(228_000L, service.displayDurationMs)
                assertFalse("Unprepared restored item has no seek command", service.canSeekCurrent)

                service.player.seekToNextMediaItem()
                assertEquals(id(2), service.player.currentMediaItem?.mediaId)
                assertEquals(0L, service.displayDurationMs)

                service.player.seekToPreviousMediaItem()
                assertEquals(id(1), service.player.currentMediaItem?.mediaId)
                assertEquals(228_000L, service.displayDurationMs)
            }
        } finally {
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            QueueStateFileStore(context).clear()
        }
    }

    @OptIn(UnstableApi::class)
    @Test
    fun knownDurationSurvivesRealMedia3ReprepareAndCheckpoint() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = QueueStateFileStore(context)
        store.clear()
        val initial = checkpoint().copy(durationMs = 0L)
        store.writeLayoutAndPosition(initial.layout(), initial.position())
        val unknownSourceFactory = UnknownBlockingDataSourceFactory()
        val controller = onMain { Robolectric.buildService(EnoTonePlaybackService::class.java).create() }
        var knownDuration = 0L
        var saved: PersistedQueueState? = null
        try {
            val service = controller.get()
            knownDuration = onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    (1..3).map(::track), initial, false, false))
                val item = MediaItem.Builder()
                    .setMediaId(id(1))
                    .setUri("https://example.invalid/fixture.wav")
                    .build()
                val source = ProgressiveMediaSource.Factory(WavDataSourceFactory())
                    .createMediaSource(item)
                (service.player as ExoPlayer).setMediaSources(listOf(source), 0, 0L)
                service.player.prepare()
                awaitReadyDuration(service)
            }
            onMain {
                val item = MediaItem.Builder()
                    .setMediaId(id(1))
                    .setUri("https://example.invalid/fixture.wav")
                    .build()
                val source = ProgressiveMediaSource.Factory(unknownSourceFactory)
                    .createMediaSource(item)
                (service.player as ExoPlayer).setMediaSources(listOf(source), 0, 0L)
                service.player.prepare()
                service.player.play()
            }
            assertTrue("unknown source should begin", unknownSourceFactory.awaitOpen())
            onMain {
                assertEquals(Player.STATE_BUFFERING, service.player.playbackState)
                assertEquals(C.TIME_UNSET, service.player.duration)
                assertEquals(knownDuration, service.displayDurationMs)
                service.pause()
            }
        } finally {
            unknownSourceFactory.release.countDown()
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            saved = store.read()
            store.clear()
        }
        assertTrue("checkpoint should survive reprepare", saved != null)
        assertEquals(knownDuration, requireNotNull(saved).durationMs)
    }

    private fun <T> onMain(block: () -> T): T {
        var outcome: Result<T>? = null
        Shadows.shadowOf(Looper.getMainLooper()).runPaused {
            outcome = runCatching(block)
        }
        return requireNotNull(outcome).getOrThrow()
    }

    private fun awaitReadyDuration(service: EnoTonePlaybackService): Long {
        repeat(100) {
            val duration = onMain {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                service.player.duration.takeIf {
                    service.player.playbackState == Player.STATE_READY && it > 0L
                }
            }
            if (duration != null) return duration
            Thread.sleep(25L)
        }
        error("Media3 fixture did not become READY with a known duration")
    }

    private class WavDataSourceFactory : DataSource.Factory {
        override fun createDataSource(): DataSource = ByteArrayDataSource(wav())

        private fun wav(): ByteArray {
            val sampleRate = 8_000
            val dataSize = sampleRate
            val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
            buffer.putInt(36 + dataSize)
            buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
            buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
            buffer.putInt(16)
            buffer.putShort(1)
            buffer.putShort(1)
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate)
            buffer.putShort(1)
            buffer.putShort(8)
            buffer.put("data".toByteArray(Charsets.US_ASCII))
            buffer.putInt(dataSize)
            repeat(dataSize) { buffer.put(0x80.toByte()) }
            return buffer.array()
        }
    }

    private class UnknownBlockingDataSourceFactory : DataSource.Factory {
        private val opened = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun createDataSource(): DataSource = object : DataSource {
            override fun addTransferListener(transferListener: TransferListener) = Unit
            override fun open(dataSpec: DataSpec): Long {
                opened.countDown()
                try {
                    release.await(20, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("fixture source interrupted")
                }
                return C.LENGTH_UNSET.toLong()
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT
            override fun getUri(): Uri? = null
            override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
            override fun close() = Unit
        }

        fun awaitOpen(): Boolean = opened.await(5, TimeUnit.SECONDS)
    }
}
