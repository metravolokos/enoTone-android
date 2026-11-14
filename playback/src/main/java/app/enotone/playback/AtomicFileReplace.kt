package app.enotone.playback

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Одна атомарная замена файла для всех мест, где приложение хранит своё состояние:
 * чекпоинт очереди, постоянные обложки, манифест выхода. Раньше каждая копия
 * вызывала `File.renameTo`, который на Windows отказывает при существующей цели —
 * второе сохранение падало, хотя на Linux (устройство) работало.
 *
 * Лежит в `:playback`, потому что `:app` уже зависит от него, а чекпоинт очереди —
 * исторически первое место с этим контрактом. Отдельный модуль ради десяти строк
 * нарушил бы порог AGENTS §4 без пользы.
 */
object AtomicFileReplace {
    /**
     * Заменяет [target] содержимым [temp] и удаляет временный файл.
     * `true` — содержимое на месте; `false` — замену выполнить не удалось
     * (вызывающий решает, бросать исключение или молча деградировать).
     */
    fun replace(temp: File, target: File): Boolean {
        return try {
            try {
                Files.move(
                    temp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                // Some Android/vendor file systems cannot move atomically; the copy
                // fallback still replaces the target, which is the property that matters.
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
