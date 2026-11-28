package app.enotone.android

import android.content.Context
import android.os.Looper
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.enotone.playback.EnoTonePlaybackService
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.QueueStateFileStore
import app.enotone.playback.QueueTrack
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * R-2 (ревью PR #160): при ожидающей записи структуры финальная позиция не
 * ставится отдельной записью — она уже входит в пару layout+position.
 *
 * Почему это дефект, а не мелочь. `onDestroy` до исправления сбрасывал
 * `layoutWritePending = false` и вызывал `savePosition(urgent = true)`.
 * `activeRevision` в этот момент ещё прежний — он обновляется только колбэком
 * после записи пары. Исполнитель `io` однопоточный, поэтому порядок был:
 *
 *   1. writeLayoutAndPosition: структура и позиция НОВОЙ ревизии;
 *   2. writePosition: позиция ПРЕЖНЕЙ ревизии поверх той же структуры.
 *
 * На диске оставалась структура новой ревизии с позицией старой.
 * `readWithStatus()` такой файл отвергает как `REVISION_MISMATCH` и откатывается
 * на предыдущую пару — то есть восстановление возвращало **прежнюю** очередь,
 * а только что включённая (новая) терялась.
 *
 * После исправления `layoutWritePending` не сбрасывается: `savePosition` сам
 * выходит по этому флагу, а пара новой ревизии остаётся целой. Если до `onDestroy`
 * трек сменился уже после снятия position snapshot, служба ставит финальную
 * позицию новой ревизии отдельной задачей *за* записью пары; completion callback
 * на main для этого не используется, потому что после destroy он уже ничего не сохраняет.
 *
 * Признак проверки — выбранный трек после восстановления: у новой очереди это
 * `id(2)` (либо `id(3)` после последующего native Next), у прежней — `id(1)`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueueLayoutPendingDestroyTest {

    private fun id(n: Int) = n.toString(16).padStart(64, '0')

    private fun track(n: Int) = QueueTrack(id(n), "Fixture $n", "/AllMusic/$n.mp3", 1_024L, null)

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

    @Test fun destroyWithPendingLayoutKeepsTheNewQueueInsteadOfRollingBack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = QueueStateFileStore(context)
        store.clear()
        val initial = checkpoint()
        store.writeLayoutAndPosition(initial.layout(), initial.position())

        val controller = onMain {
            Robolectric.buildService(EnoTonePlaybackService::class.java).create()
        }
        val service = controller.get()
        onMain {
            assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                (1..3).map(::track), initial, false, false))
            // Новая очередь с другим выбранным треком. Запись пары поставлена,
            // колбэк на main ещё не доставлен — layoutWritePending == true.
            service.playQueue("test@example.invalid", "fixture-password",
                (1..3).map(::track), id(2), false, initial.sourceKey)
            controller.destroy()
        }
        // `onDestroy` вызывает `io.shutdown()` — упорядоченный flush. Терминация
        // исполнителя записи и есть детерминированный момент «все записи закончены»:
        // ждать переставания файлов по `lastModified` нельзя, потому что на части ФС
        // гранулярность метки секундная, а пара пишется в два файла, и чтение между
        // ними давало несогласованную пару (`state == null`).
        assertTrue(
            "исполнитель записи должен завершиться после onDestroy",
            service.awaitQueueWritesFinished(10_000L),
        )
        val read = QueueStateFileStore(context).readWithStatus()
        assertTrue("пара должна читаться: ${read.code}", read.state != null)
        org.junit.Assert.assertNotEquals(
            "восстановление не должно откатываться на прежнюю пару: лишняя запись позиции " +
                "прежней ревизии поверх новой структуры ломала пару и стирала только что " +
                "включённую очередь",
            initial.queueRevision,
            read.state?.queueRevision,
        )
        QueueStateFileStore(context).clear()
    }

    @Test fun destroyAfterNativeNextDuringPendingLayoutPersistsLatestSelectedTrack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = QueueStateFileStore(context)
        store.clear()
        val initial = checkpoint()
        store.writeLayoutAndPosition(initial.layout(), initial.position())

        val controller = onMain {
            Robolectric.buildService(EnoTonePlaybackService::class.java).create()
        }
        val service = controller.get()
        onMain {
            assertTrue(service.restoreQueue("test@example.invalid", "fixture-password",
                (1..3).map(::track), initial, false, false))
            service.playQueue("test@example.invalid", "fixture-password",
                (1..3).map(::track), id(2), false, initial.sourceKey)
            service.player.seekToNextMediaItem()
            org.junit.Assert.assertEquals(id(3), service.player.currentMediaItem?.mediaId)
            controller.destroy()
        }
        assertTrue(service.awaitQueueWritesFinished(10_000L))

        val saved = store.read()
        org.junit.Assert.assertNotNull("checkpoint should survive destroy", saved)
        org.junit.Assert.assertEquals(id(3), saved?.selectedTrackId)
        QueueStateFileStore(context).clear()
    }

    @Test fun destroyDoesNotThrowWhenExecutorIsShutDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = QueueStateFileStore(context)
        store.clear()
        val initial = checkpoint()
        store.writeLayoutAndPosition(initial.layout(), initial.position())

        val controller = onMain {
            Robolectric.buildService(EnoTonePlaybackService::class.java).create()
        }
        val service = controller.get()
        onMain {
            service.restoreQueue("test@example.invalid", "fixture-password",
                (1..3).map(::track), initial, false, false)
            controller.destroy()
        }
        // Отложенные колбэки после shutdown не должны бросать на main.
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        QueueStateFileStore(context).clear()
    }

    private fun <T> onMain(block: () -> T): T {
        var outcome: Result<T>? = null
        Shadows.shadowOf(Looper.getMainLooper()).runPaused {
            outcome = runCatching(block)
        }
        return requireNotNull(outcome).getOrThrow()
    }
}
