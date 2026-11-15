package app.enotone.playback

import android.content.Context

/**
 * Безопасное создание временного кэша воспроизведения.
 *
 * `TransientMediaCache` в конструкторе удаляет каталог (до 512 МиБ по политике),
 * создаёт его заново и открывает `SimpleCache` с SQLite. Выполнять это на
 * главном потоке нельзя: холодный старт воспроизведения превращался в удаление
 * тысяч файлов на UI-потоке (ANR).
 *
 * Второе: любая из этих операций может упасть — каталог не удаляется, хранилище
 * заполнено, БД занята. Раньше `IllegalStateException` уходил наверх и
 * воспроизведение не начиналось вовсе, хотя потоковый режим полностью
 * поддержан. Теперь сбой означает «играем без кэша», а не «музыки нет».
 */
object TransientCacheFactory {
    /** Тестовый шов; в продакшене `null`. Не читается вне `createTransientCacheSafely`. */
    @Volatile
    var testOverride: ((Context) -> Decision)? = null

    /** Что делать с кэшем для текущей очереди. */
    sealed interface Decision {
        /** Кэш доступен и будет использован. */
        data class UseCache(val cache: TransientMediaCache) : Decision

        /** Кэш недоступен: играем через сеть, причину показываем кодом. */
        data class PlayWithoutCache(val reason: TransientCacheFailure) : Decision
    }
}

/** Почему кэш не удалось подготовить. Только код, без путей и текстов исключений. */
enum class TransientCacheFailure {
    /** Каталог кэша не удалось очистить. */
    RESET_FAILED,

    /** Каталог кэша не удалось создать. */
    DIRECTORY_UNAVAILABLE,

    /** `SimpleCache`/SQLite не открылись. */
    OPEN_FAILED,
}

/**
 * Создаёт кэш или сообщает причину отказа. Никогда не бросает:
 * вызывающий код должен продолжить воспроизведение в потоковом режиме.
 *
 * Должен вызываться вне главного потока.
 */
@Suppress("TooGenericExceptionCaught")
fun createTransientCacheSafely(context: Context): TransientCacheFactory.Decision =
    TransientCacheFactory.testOverride?.invoke(context) ?: createTransientCacheReal(context)

@Suppress("TooGenericExceptionCaught")
private fun createTransientCacheReal(context: Context): TransientCacheFactory.Decision =
    try {
        TransientCacheFactory.Decision.UseCache(TransientMediaCache(context))
    } catch (failure: IllegalStateException) {
        // Конструктор кэша сообщает причину только этими двумя строками;
        // текст исключения содержит путь к приватному каталогу и наружу не идёт.
        val reason = when (failure.message) {
            "Temporary cache reset failed" -> TransientCacheFailure.RESET_FAILED
            "Temporary cache directory unavailable" -> TransientCacheFailure.DIRECTORY_UNAVAILABLE
            else -> TransientCacheFailure.OPEN_FAILED
        }
        TransientCacheFactory.Decision.PlayWithoutCache(reason)
    } catch (_: Exception) {
        TransientCacheFactory.Decision.PlayWithoutCache(TransientCacheFailure.OPEN_FAILED)
    }

/**
 * Тестовый шов: позволяет детерминированно смоделировать недоступный кэш.
 * В продакшене всегда `null` — поведение не меняется.
 */
fun setTransientCacheOverrideForTests(
    override: ((Context) -> TransientCacheFactory.Decision)?,
) {
    TransientCacheFactory.testOverride = override
}
