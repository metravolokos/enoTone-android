package app.enotone.android

import android.content.Context
import android.os.Looper
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.enotone.playback.EnoTonePlaybackService
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.QueueEditSource
import app.enotone.playback.QueueEditStatus
import app.enotone.playback.QueueStateFileStore
import app.enotone.playback.QueueTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.UUID

/** Real Media3 timeline and no-network paused restoration; UI belongs to 8b. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueueEditServiceTest {
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

    @Test fun insertingNextChangesNativeSkipAndRecoversAfterServiceRestart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileStore = QueueStateFileStore(context)
        fileStore.clear()
        val initial = checkpoint()
        fileStore.writeLayoutAndPosition(initial.layout(), initial.position())
        val first = onMain { Robolectric.buildService(EnoTonePlaybackService::class.java).create() }
        try {
            val service = first.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    (1..3).map(::track), initial, false, false))
                assertEquals(id(1), service.player.currentMediaItem?.mediaId)
                assertEquals(QueueEditStatus.APPLIED, service.insertNext(listOf(track(4))).status)
                assertEquals(14_000L, service.player.currentPosition)
                assertEquals(Player.REPEAT_MODE_OFF, service.player.repeatMode)
                assertEquals(listOf(id(1), id(4), id(2), id(3)),
                    (0 until service.player.mediaItemCount).map {
                        service.player.getMediaItemAt(it).mediaId
                    })
                // The native MediaSession/headphone Next must follow the edited timeline.
                service.player.seekToNextMediaItem()
                assertEquals(id(4), service.player.currentMediaItem?.mediaId)
            }
            val persisted = waitForRevision(fileStore, initial.queueRevision)
            assertEquals(QueueEditSource.edited(initial.sourceKey), persisted.sourceKey)
            assertEquals(listOf(id(1), id(4), id(2), id(3)), persisted.trackIds)
            assertEquals(id(4), persisted.selectedTrackId)
            assertEquals(0L, persisted.positionMs)
        } finally {
            onMain { first.destroy() }
        }
        val restored = QueueStateFileStore(context).read()!!
        val second = onMain { Robolectric.buildService(EnoTonePlaybackService::class.java).create() }
        try {
            val service = second.get()
            val byId = (1..4).map(::track).associateBy { it.id }
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    restored.trackIds.map(byId::getValue), restored, false, false))
                assertEquals(id(4), service.player.currentMediaItem?.mediaId)
                assertEquals(listOf(id(1), id(4), id(2), id(3)),
                    (0 until service.player.mediaItemCount).map {
                        service.player.getMediaItemAt(it).mediaId
                    })
            }
        } finally {
            onMain { second.destroy() }
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun repeatedSingleTrackModeAndCurrentPositionSurviveAppend() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val initial = checkpoint().copy(repeatMode = Player.REPEAT_MODE_ONE)
        val controller = onMain { Robolectric.buildService(EnoTonePlaybackService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    (1..3).map(::track), initial, false, false))
                val beforePosition = service.player.currentPosition
                assertEquals(QueueEditStatus.APPLIED, service.append(listOf(track(4))).status)
                assertEquals(Player.REPEAT_MODE_ONE, service.player.repeatMode)
                assertEquals(id(1), service.player.currentMediaItem?.mediaId)
                assertEquals(beforePosition, service.player.currentPosition)
            }
        } finally {
            onMain { controller.destroy() }
            QueueStateFileStore(context).clear()
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var outcome: Result<T>? = null
        Shadows.shadowOf(Looper.getMainLooper()).runPaused {
            outcome = runCatching(block)
        }
        return requireNotNull(outcome).getOrThrow()
    }

    private fun waitForRevision(store: QueueStateFileStore, old: String): PersistedQueueState {
        repeat(100) {
            // Robolectric's paused main looper must deliver the layout-write
            // completion callback before the subsequent transition checkpoint.
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val checkpoint = store.read()
            if (checkpoint != null && checkpoint.queueRevision != old &&
                checkpoint.selectedTrackId == id(4)) return checkpoint
            Thread.sleep(20L)
        }
        error("New queue layout was not written")
    }
}
