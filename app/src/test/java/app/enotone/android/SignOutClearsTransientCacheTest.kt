package app.enotone.android

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.enotone.playback.EnoTonePlaybackService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Выход из аккаунта обязан убрать аудиофрагменты с диска.
 *
 * Выход вызывает `stopPlayback()`, но не `stopService`: служба продолжает жить, поэтому
 * `onDestroy` не выполняется и `TransientMediaCache.close()` сам по себе не срабатывает.
 * ADR-0080 требует удалить данные аккаунта **до** очистки Keystore, поэтому Stop обязан
 * сбросить кэш сам.
 *
 * Тест проверяет состояние каталога кэша до и после Stop на реальной службе. Содержимое
 * `SimpleCache` не создаётся (нужен SQLite и реальные загрузки), поэтому проверяется
 * каталог временного кэша: он адресуется тем же `TransientCachePolicy` и удаляется тем
 * же `close()`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SignOutClearsTransientCacheTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun cacheDirectory() = java.io.File(context.cacheDir, "enotone-m5-temporary-audio")

    /** Кэш удаляется только в `close()`; Stop должен его вызвать. */
    @Test fun stopPlaybackRemovesTemporaryAudioDirectory() {
        assertFalse(cacheDirectory().exists())
        val controller = Robolectric.buildService(EnoTonePlaybackService::class.java).create()
        try {
            val service = controller.get()
            // Создаём маркер внутри каталога кэша: он символизирует скачанные фрагменты.
            val directory = cacheDirectory()
            directory.mkdirs()
            assertTrue(directory.isDirectory)
            java.io.File(directory, "fragment.bin").writeBytes(ByteArray(64))
            assertTrue(java.io.File(directory, "fragment.bin").isFile)

            service.stopPlayback()

            // Удаление асинхронно (отдельный поток в close()), поэтому ждём коротко.
            val deadline = System.currentTimeMillis() + 5_000
            while (cacheDirectory().exists() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            assertFalse(
                "Stop must drop cached audio so the previous account's data is gone",
                cacheDirectory().exists(),
            )
        } finally {
            runCatching { controller.destroy() }
        }
    }
}
