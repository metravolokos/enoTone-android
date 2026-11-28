package app.enotone.android

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.enotone.playback.EnoTonePlaybackService
import app.enotone.playback.TransientCacheFactory
import app.enotone.playback.TransientCacheFailure
import app.enotone.playback.setTransientCacheOverrideForTests
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Выход из аккаунта обязан убрать аудиофрагменты с диска.
 *
 * Выход идёт через `stopPlaybackAndEraseData()`, а не `stopPlayback()`: последний лишь
 * останавливает плеер, а удаление каталога и индекса Media3 живёт в erase-методе
 * (перенесено туда в #288, FAIL-CLOSED-SIGNOUT). Метод возвращает future, который
 * завершается **после** фактического удаления, поэтому тест ждёт его детерминированно,
 * а не по таймеру: фиксированный дедлайн давал флейк на нагруженном CI.
 *
 * Асинхронное `prepareTransientCache()` при создании службы нейтрализуется штатным
 * швом `setTransientCacheOverrideForTests` (в продакшене он `null`): иначе фоновое
 * открытие `SimpleCache` пересоздавало каталог и исход зависел от планировщика.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SignOutClearsTransientCacheTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun cacheDirectory() = File(context.cacheDir, "enotone-m5-temporary-audio")

    @Test fun stopPlaybackAndEraseDataRemovesTemporaryAudioDirectory() {
        // Никакого кэша за пределами теста: каталог адресуется только этим тестом.
        setTransientCacheOverrideForTests {
            TransientCacheFactory.Decision.PlayWithoutCache(TransientCacheFailure.OPEN_FAILED)
        }
        try {
            assertFalse(cacheDirectory().exists())
            val controller = Robolectric.buildService(EnoTonePlaybackService::class.java).create()
            try {
                val service = controller.get()
                // Создаём маркер внутри каталога кэша: он символизирует скачанные фрагменты.
                val directory = cacheDirectory()
                directory.mkdirs()
                assertTrue(directory.isDirectory)
                File(directory, "fragment.bin").writeBytes(ByteArray(64))
                assertTrue(File(directory, "fragment.bin").isFile)

                // Future завершается только после удаления — ждём его, не спим.
                service.stopPlaybackAndEraseData().get(10, TimeUnit.SECONDS)

                assertFalse(
                    "Sign-out must drop cached audio so the previous account's data is gone",
                    cacheDirectory().exists(),
                )
            } finally {
                runCatching { controller.destroy() }
            }
        } finally {
            setTransientCacheOverrideForTests(null)
        }
    }
}
