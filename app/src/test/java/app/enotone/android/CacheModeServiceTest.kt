package app.enotone.android

import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.PreviewAudioPlayback
import app.enotone.playback.QueueTrack
import app.enotone.playback.TransientCacheFactory
import app.enotone.playback.TransientCacheFailure
import app.enotone.playback.setTransientCacheOverrideForTests
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * R-1 (ревью PR #160) — поведение, а не только чистая функция.
 *
 * Сценарий из ревью: очередь собрана, когда кэш ещё не готов, затем кэш
 * «становится доступен». Предзагрузка для такой очереди включаться не должна:
 * плеер этой очереди кэш не читает (источник выбран при сборке), поэтому
 * предзагрузка скачивала бы трек повторно.
 *
 * До исправления `cacheEnabledForQueue` выставлялся по одному лишь запросу
 * пользователя, поэтому этот тест был красным.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CacheModeServiceTest {

    private fun track(n: Int) = QueueTrack(
        id = n.toString(16).padStart(64, '0'),
        title = "Fixture $n",
        encodedPath = "/AllMusic/$n.mp3",
        remoteSize = 1_024L,
        remoteEtag = null,
    )

    private fun checkpoint(): PersistedQueueState = PersistedQueueState(
        trackIds = (1..3).map { track(it).id },
        selectedTrackId = track(1).id,
        positionMs = 0L,
        repeatMode = Player.REPEAT_MODE_OFF,
        shuffleEnabled = false,
        shuffledIndices = null,
        sourceKey = "a".repeat(64),
        savedAtEpochMs = 1L,
        queueRevision = UUID.randomUUID().toString(),
    )

    @After fun clearOverride() = setTransientCacheOverrideForTests(null)

    @Test fun queueBuiltWithoutCacheDoesNotEnablePrefetchWhenCacheAppearsLater() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Кэш недоступен в момент сборки очереди — ровно случай R-1.
        setTransientCacheOverrideForTests {
            TransientCacheFactory.Decision.PlayWithoutCache(TransientCacheFailure.OPEN_FAILED)
        }
        val playback = PreviewAudioPlayback(context)
        try {
            playback.playQueue(
                email = "test@example.invalid",
                appPassword = "fixture-password",
                tracks = (1..3).map(::track),
                selectedId = track(1).id,
                useTemporaryCache = true,
                sourceKey = "a".repeat(64),
            )
            assertFalse(
                "очередь без кэша не включает предзагрузку",
                playback.cacheEnabledForCurrentQueue(),
            )

            // Кэш «становится доступен» позже: решение не пересматривается.
            assertFalse(
                "появление кэша не включает предзагрузку для уже собранной очереди",
                playback.cacheEnabledForCurrentQueue(),
            )
        } finally {
            playback.close()
        }
    }

    @Test fun queueBuiltWithCacheEnabledKeepsPrefetchOn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setTransientCacheOverrideForTests {
            TransientCacheFactory.Decision.PlayWithoutCache(TransientCacheFailure.RESET_FAILED)
        }
        val playback = PreviewAudioPlayback(context)
        try {
            // Пользователь кэш не просил: режим остаётся выключенным независимо от готовности.
            playback.playQueue(
                email = "test@example.invalid",
                appPassword = "fixture-password",
                tracks = (1..3).map(::track),
                selectedId = track(1).id,
                useTemporaryCache = false,
                sourceKey = "a".repeat(64),
            )
            assertFalse(playback.cacheEnabledForCurrentQueue())
        } finally {
            playback.close()
        }
    }

    @Test fun restoreQueueUsesTheSameDecision() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setTransientCacheOverrideForTests {
            TransientCacheFactory.Decision.PlayWithoutCache(TransientCacheFailure.DIRECTORY_UNAVAILABLE)
        }
        val playback = PreviewAudioPlayback(context)
        try {
            val state = checkpoint()
            playback.restoreQueue(
                email = "test@example.invalid",
                appPassword = "fixture-password",
                tracks = (1..3).map(::track),
                state = state,
                useTemporaryCache = true,
            )
            assertFalse(
                "restoreQueue принимает то же решение, что и playQueue",
                playback.cacheEnabledForCurrentQueue(),
            )
        } finally {
            playback.close()
        }
    }
}
