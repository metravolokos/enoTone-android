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
import androidx.test.core.app.ApplicationProvider
import app.enotone.playback.EnoTonePlaybackService
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.PlaybackQueueSnapshot
import app.enotone.playback.PreviewAudioPlayback
import app.enotone.playback.QueueEditSource
import app.enotone.playback.QueueEditStatus
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
import java.io.FileNotFoundException
import java.io.InterruptedIOException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
                assertEquals(PlaybackQueueSnapshot((1..3).map(::id), currentIndex = 0),
                    service.queueSnapshot)
                assertEquals(QueueEditStatus.APPLIED, service.insertNext(listOf(track(4))).status)
                assertEquals(14_000L, service.player.currentPosition)
                assertEquals(Player.REPEAT_MODE_OFF, service.player.repeatMode)
                assertEquals(PlaybackQueueSnapshot(listOf(id(1), id(4), id(2), id(3)), 0),
                    service.queueSnapshot)
                assertEquals(listOf(id(1), id(4), id(2), id(3)),
                    (0 until service.player.mediaItemCount).map {
                        service.player.getMediaItemAt(it).mediaId
                    })
                // The native MediaSession/headphone Next must follow the edited timeline.
                service.player.seekToNextMediaItem()
                assertEquals(id(4), service.player.currentMediaItem?.mediaId)
                assertEquals(PlaybackQueueSnapshot(listOf(id(1), id(4), id(2), id(3)), 1),
                    service.queueSnapshot)
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
            // destroy() schedules a final position write on the service IO lane and
            // shuts it down; clear() must run after that lane drained, otherwise the
            // checkpoint reappears right after deletion.
            controller.get().awaitQueueWritesFinished(10_000L)
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun retryCurrentWithoutErrorDoesNotChangeRestoredQueueOrPosition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val initial = checkpoint().copy(selectedTrackId = id(2), positionMs = 14_000L)
        val controller = onMain { Robolectric.buildService(EnoTonePlaybackService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    (1..3).map(::track), initial, false, false))
                val before = service.queueSnapshot

                assertFalse(service.retryCurrent())

                assertEquals(before, service.queueSnapshot)
                assertEquals(id(2), service.player.currentMediaItem?.mediaId)
                assertEquals(14_000L, service.player.currentPosition)
                assertEquals(Player.STATE_IDLE, service.player.playbackState)
            }
        } finally {
            onMain { controller.destroy() }
            // destroy() schedules a final position write on the service IO lane and
            // shuts it down; clear() must run after that lane drained, otherwise the
            // checkpoint reappears right after deletion.
            controller.get().awaitQueueWritesFinished(10_000L)
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun retryCurrentAfterPlayerErrorKeepsCurrentItemIndexAndPosition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val source = RetryDataSourceFactory()
        BufferingService.dataSourceFactory = source
        val queue = (1..3).map { track(it).copy(remoteSize = 100_000L) }
        val initial = checkpoint().copy(selectedTrackId = id(2), positionMs = 14_000L)
        val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    queue, initial, false, false))
                service.resume()
            }
            assertTrue("initial read should fail", awaitPlayerError(service))
            val before = onMain {
                Triple(service.player.currentMediaItem?.mediaId, service.queueSnapshot,
                    service.player.currentPosition)
            }
            assertEquals(id(2), before.first)
            assertEquals(PlaybackQueueSnapshot((1..3).map(::id), currentIndex = 1), before.second)
            assertEquals(14_000L, before.third)

            source.allowOpen = true
            assertTrue(onMain { service.retryCurrent() })
            assertTrue("retry should open the same file", source.awaitSuccessfulOpen())

            onMain {
                assertEquals(before.first, service.player.currentMediaItem?.mediaId)
                assertEquals(before.second, service.queueSnapshot)
                assertEquals(before.third, service.player.currentPosition)
                assertEquals(listOf(id(1), id(2), id(3)),
                    (0 until service.player.mediaItemCount).map {
                        service.player.getMediaItemAt(it).mediaId
                    })
            }
        } finally {
            source.release.countDown()
            onMain { controller.destroy() }
            BufferingService.dataSourceFactory = null
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun invalidNewRouteReportsFailureAndKeepsCurrentTimeline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val controller = onMain { Robolectric.buildService(EnoTonePlaybackService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    (1..3).map(::track), checkpoint(), false, false))
                assertEquals(QueueEditStatus.FAILED,
                    service.insertNext(listOf(track(4).copy(remoteSize = 0L))).status)
                assertEquals(listOf(id(1), id(2), id(3)),
                    (0 until service.player.mediaItemCount).map { service.player.getMediaItemAt(it).mediaId })
                assertEquals(id(1), service.player.currentMediaItem?.mediaId)
                assertEquals(14_000L, service.player.currentPosition)
                assertEquals(QueueEditStatus.APPLIED, service.insertNext(listOf(track(4))).status)
            }
        } finally {
            onMain { controller.destroy() }
            // destroy() schedules a final position write on the service IO lane and
            // shuts it down; clear() must run after that lane drained, otherwise the
            // checkpoint reappears right after deletion.
            controller.get().awaitQueueWritesFinished(10_000L)
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun snapshotUsesShuffleTraversalAndQueueSelectionPlaysThatPosition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val initial = checkpoint().copy(
            shuffleEnabled = true,
            shuffledIndices = intArrayOf(2, 0, 1),
        )
        val controller = onMain { Robolectric.buildService(EnoTonePlaybackService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                    (1..3).map(::track), initial, false, false))
                assertEquals(
                    PlaybackQueueSnapshot(listOf(id(3), id(1), id(2)), currentIndex = 1),
                    service.queueSnapshot,
                )
                // Tapping the selected row is an explicit no-op.
                assertTrue(service.skipToQueuePosition(1))
                assertEquals(id(1), service.player.currentMediaItem?.mediaId)
                assertTrue(service.skipToQueuePosition(0))
                assertEquals(id(3), service.player.currentMediaItem?.mediaId)
                assertEquals(PlaybackQueueSnapshot(listOf(id(3), id(1), id(2)), 0),
                    service.queueSnapshot)
                assertTrue(service.player.playWhenReady)
                assertTrue(!service.skipToQueuePosition(3))
            }
        } finally {
            onMain { controller.destroy() }
            // destroy() schedules a final position write on the service IO lane and
            // shuts it down; clear() must run after that lane drained, otherwise the
            // checkpoint reappears right after deletion.
            controller.get().awaitQueueWritesFinished(10_000L)
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun selectingAnUnstagedTrackKeepsTheFullQueueWithoutDuplicates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 100).map(::track)
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(0),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = false,
            shuffledIndices = null,
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
                assertEquals(3, service.loadedQueueSize)
                assertTrue(service.skipToQueuePosition(70))
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
                val loadedIds = (0 until service.player.mediaItemCount).map {
                    service.player.getMediaItemAt(it).mediaId
                }
                assertTrue(service.player.mediaItemCount > 70)
                assertEquals(queue.map { it.id }.take(service.player.mediaItemCount), loadedIds)
                assertEquals(id(70), service.player.currentMediaItem?.mediaId)
                assertEquals(PlaybackQueueSnapshot(queue.map { it.id }, 70), service.queueSnapshot)
            }
        } finally {
            onMain { controller.destroy() }
            // destroy() schedules a final position write on the service IO lane and
            // shuts it down; clear() must run after that lane drained, otherwise the
            // checkpoint reappears right after deletion.
            controller.get().awaitQueueWritesFinished(10_000L)
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun enablingShuffleKeepsCurrentTrackFirstAndToggleOffRestoresPhysicalOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 3_590).map(::track)
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(50),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = false,
            shuffledIndices = null,
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
                assertEquals(PlaybackQueueSnapshot(queue.map { it.id }, 50), service.queueSnapshot)

                service.toggleShuffle()
                val shuffled = requireNotNull(service.queueSnapshot)
                assertEquals(id(50), shuffled.trackIds.first())
                assertEquals(0, shuffled.currentIndex)
                assertEquals(queue.size, shuffled.trackIds.size)
                assertEquals(queue.mapTo(mutableSetOf()) { it.id }, shuffled.trackIds.toSet())

                service.toggleShuffle()
                assertEquals(PlaybackQueueSnapshot(queue.map { it.id }, 50), service.queueSnapshot)
            }
        } finally {
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun media3NextFollowsAnchoredShuffleOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 4).map(::track)
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(2),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = false,
            shuffledIndices = null,
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
                service.toggleShuffle()
                val order = requireNotNull(service.queueSnapshot).trackIds
                assertEquals(id(2), order.first())

                service.player.seekToNextMediaItem()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals(order[1], service.player.currentMediaItem?.mediaId)
                service.player.seekToPreviousMediaItem()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals(id(2), service.player.currentMediaItem?.mediaId)
            }
        } finally {
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun newShuffleQueueKeepsSelectedTrackFirstWhenShuffleWasAlreadyEnabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 100).map(::track)
        val source = BlockingDataSourceFactory()
        BufferingService.dataSourceFactory = source
        val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                service.player.shuffleModeEnabled = true
                service.playQueue("test@example.invalid", "fixture-password", queue,
                    id(30), false, id(99))
            }
            source.release.countDown()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            onMain {
                val shuffled = requireNotNull(service.queueSnapshot)
                assertEquals(id(30), service.player.currentMediaItem?.mediaId)
                assertEquals(id(30), shuffled.trackIds.first())
                assertEquals(0, shuffled.currentIndex)
                assertEquals(queue.size, shuffled.trackIds.size)
                assertEquals(queue.mapTo(mutableSetOf()) { it.id }, shuffled.trackIds.toSet())
            }
        } finally {
            source.release.countDown()
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            BufferingService.dataSourceFactory = null
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun fullTenThousandQueueEvictsPlayedHistoryAndPersistsNativeNext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = QueueStateFileStore(context)
        store.clear()
        val queue = (0 until 10_000).map(::track)
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(5_000),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = false,
            shuffledIndices = null,
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
                assertEquals(10_000, service.queueSize)
                val editStartedNs = System.nanoTime()
                val editResult = service.insertNext(listOf(track(10_000))).status
                val editElapsedMs = (System.nanoTime() - editStartedNs) / 1_000_000.0
                assertTrue("Full-queue edit blocked main thread for $editElapsedMs ms",
                    editElapsedMs < 2_000)
                assertEquals(QueueEditStatus.APPLIED, editResult)

                val snapshot = requireNotNull(service.queueSnapshot)
                assertEquals(10_000, snapshot.trackIds.size)
                assertFalse(id(0) in snapshot.trackIds)
                assertEquals(id(5_000), snapshot.trackIds[snapshot.currentIndex])
                assertEquals(id(10_000), snapshot.trackIds[snapshot.currentIndex + 1])
                assertTrue(id(5_001) in snapshot.trackIds)

                service.player.seekToNextMediaItem()
                assertEquals(id(10_000), service.player.currentMediaItem?.mediaId)
            }
            val saved = waitForLayout(store, initial.queueRevision, id(10_000))
            assertEquals(10_000, saved.trackIds.size)
            assertFalse(id(0) in saved.trackIds)
            assertEquals(id(5_000), saved.trackIds[saved.trackIds.indexOf(id(10_000)) - 1])
            assertEquals(QueueEditSource.edited(initial.sourceKey), saved.sourceKey)
        } finally {
            onMain { controller.destroy() }
            controller.get().awaitQueueWritesFinished(10_000L)
            store.clear()
        }
    }

    @Test fun readdingAnExistingTrackKeepsTheRouteAndPersistsTheQueue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = QueueStateFileStore(context)
        store.clear()
        val queue = (1..100).map(::track)
        val initial = PersistedQueueState(
            trackIds = queue.map { it.id },
            selectedTrackId = id(2),
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = false,
            shuffledIndices = null,
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
                assertEquals(QueueEditStatus.APPLIED, service.insertNext(listOf(track(101))).status)
                assertEquals(101, service.queueSize)
                assertEquals(id(2), service.player.currentMediaItem?.mediaId)
                assertEquals(id(101), service.queueSnapshot?.trackIds?.get(2))

                // Moving an existing track must keep one copy and a valid route.
                service.player.seekToNextMediaItem()
                assertEquals(id(101), service.player.currentMediaItem?.mediaId)
                assertEquals(QueueEditStatus.APPLIED, service.insertNext(listOf(track(1))).status)
                assertEquals(101, service.queueSize)
                assertEquals(id(101), service.player.currentMediaItem?.mediaId)
                assertEquals(id(1), service.queueSnapshot?.trackIds?.get(2))
            }
            val saved = waitForLayout(store, initial.queueRevision, id(101))
            assertEquals(101, saved.trackIds.size)
            assertEquals(id(101), saved.selectedTrackId)
            assertEquals(id(1), saved.trackIds[2])
            assertEquals(QueueEditSource.edited(initial.sourceKey), saved.sourceKey)
        } finally {
            onMain { controller.destroy() }
            store.clear()
        }
    }

    @Test fun aBufferingPlayerStagesAndStartsAnUnloadedQueueTargetWithoutExtraGets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QueueStateFileStore(context).clear()
        val queue = (0 until 100).map(::track)
        val source = BlockingDataSourceFactory()
        BufferingService.dataSourceFactory = source
        val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
        try {
            val service = controller.get()
            onMain {
                service.playQueue("test@example.invalid", "fixture-password", queue,
                    id(0), false, id(99))
            }
            assertTrue("current source should begin", source.awaitOpenCount(1))
            assertEquals(1, source.openedUris.size)
            onMain {
                assertEquals(Player.STATE_BUFFERING, service.player.playbackState)
                assertTrue(service.skipToQueuePosition(99))
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
            }
            assertTrue("selected source should start before current becomes READY", source.awaitOpenCount(2))
            onMain {
                assertEquals(Player.STATE_BUFFERING, service.player.playbackState)
                assertEquals(id(99), service.player.currentMediaItem?.mediaId)
                assertEquals(100, service.loadedQueueSize)
                assertEquals(PlaybackQueueSnapshot(queue.map { it.id }, 99), service.queueSnapshot)
            }
            assertEquals("only the current item and selected target may open", 2, source.openedUris.size)
            assertTrue(source.openedUris[0].endsWith("/0.mp3"))
            assertTrue(source.openedUris[1].endsWith("/99.mp3"))
        } finally {
            source.release.countDown()
            onMain { controller.destroy() }
            BufferingService.dataSourceFactory = null
            QueueStateFileStore(context).clear()
        }
    }

    @Test fun manualNextPreviousAndSeekCancelADeferredQueueTargetDuringBuffering() {
        for ((action, expectedIndex) in listOf(
            "next" to 51,
            "previous" to 49,
            "seek" to 50,
        )) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            QueueStateFileStore(context).clear()
            val queue = (0 until 100).map(::track)
            val source = BlockingDataSourceFactory()
            BufferingService.dataSourceFactory = source
            val controller = onMain { Robolectric.buildService(BufferingService::class.java).create() }
            try {
                val service = controller.get()
                onMain {
                    service.playQueue("test@example.invalid", "fixture-password", queue,
                        id(50), false, id(99))
                }
                assertTrue("current source should begin", source.awaitOpenCount(1))
                onMain {
                    assertEquals(Player.STATE_BUFFERING, service.player.playbackState)
                    assertTrue(service.skipToQueuePosition(99))
                    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
                    when (action) {
                        "next" -> service.playNext()
                        "previous" -> service.playPrevious()
                        else -> service.player.seekTo(10_000L)
                    }
                    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
                    assertEquals("$action must cancel the queued target", id(expectedIndex),
                        service.player.currentMediaItem?.mediaId)
                }
                assertTrue("$action must not open the deferred target",
                    source.openedUris.none { it.endsWith("/99.mp3") })
            } finally {
                source.release.countDown()
                onMain { controller.destroy() }
                BufferingService.dataSourceFactory = null
                QueueStateFileStore(context).clear()
            }
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

    private fun waitForLayout(
        store: QueueStateFileStore,
        old: String,
        selectedId: String,
    ): PersistedQueueState {
        repeat(100) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val saved = store.read()
            if (saved != null && saved.queueRevision != old && saved.selectedTrackId == selectedId) {
                return saved
            }
            Thread.sleep(20L)
        }
        error("Updated queue layout was not written")
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

    private class BlockingDataSourceFactory : DataSource.Factory {
        val openedUris = CopyOnWriteArrayList<String>()
        private val firstOpen = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun createDataSource(): DataSource = object : DataSource {
            override fun addTransferListener(transferListener: TransferListener) = Unit
            override fun open(dataSpec: DataSpec): Long {
                openedUris += dataSpec.uri.toString()
                firstOpen.countDown()
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

        fun awaitOpenCount(count: Int): Boolean {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (openedUris.size < count && System.nanoTime() < deadline) Thread.sleep(5L)
            return openedUris.size >= count
        }
    }

    private class RetryDataSourceFactory : DataSource.Factory {
        @Volatile var allowOpen = false
        private val successfulOpen = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun createDataSource(): DataSource = object : DataSource {
            override fun addTransferListener(transferListener: TransferListener) = Unit
            override fun open(dataSpec: DataSpec): Long {
                if (!allowOpen) throw FileNotFoundException("fixture unavailable")
                successfulOpen.countDown()
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

        fun awaitSuccessfulOpen(): Boolean = successfulOpen.await(5, TimeUnit.SECONDS)
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
