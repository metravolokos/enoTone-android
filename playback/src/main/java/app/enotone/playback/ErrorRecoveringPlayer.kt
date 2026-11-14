package app.enotone.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * MediaSession получает этот обёрнутый плеер, а не сырой ExoPlayer.
 *
 * Кнопка Play в уведомлении, на гарнитуре и в Bluetooth-контроллере приходит
 * прямо в `Player.play()` мимо `PreviewAudioPlayback.resume()`. Без обёртки
 * исправление выхода из ошибки работало бы только в приложении, а снаружи
 * пользователь продолжал бы видеть «Ошибка… код N» и ничего не происходило.
 *
 * Делегируется всё, кроме `play()`: обёртка не должна менять ни timeline,
 * ни метаданные, ни команды — иначе регресс затронет уведомление целиком.
 */
@OptIn(UnstableApi::class)
internal class ErrorRecoveringPlayer(
    private val delegate: Player,
    private val onPlayRequested: () -> Unit,
) : ForwardingPlayer(delegate) {

    override fun play() {
        // Обычный путь (ошибки нет) не меняем: обёртка не перехватывает управление.
        if (delegate.playerError == null) {
            super.play()
            return
        }
        // Ошибка: единственный путь восстановления — политика в PreviewAudioPlayback,
        // чтобы приложение и внешние контроллеры вели себя одинаково.
        onPlayRequested()
    }
}
