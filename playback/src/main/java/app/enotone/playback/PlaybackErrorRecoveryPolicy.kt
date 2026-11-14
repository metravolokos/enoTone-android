package app.enotone.playback

/**
 * Что делать по нажатию Play, если предыдущая попытка закончилась ошибкой.
 *
 * После `onPlayerError` Media3 оставляет плеер в `STATE_IDLE` и сохраняет
 * `playerError`. `Player.play()` там только выставляет `playWhenReady` и ничего
 * не возобновляет — пользователь видит «Ошибка… код N» и не может продолжить.
 *
 * Решение владельца (2026-11-15): Play переходит к следующему треку. Если
 * переходить некуда или включён повтор одного, начинаем текущий заново —
 * иначе на последнем треке Play снова оказался бы в тупике.
 *
 * Чистая функция: только флаги, без Player, медиаданных и сети.
 */
enum class PlaybackErrorAction {
    /** Ошибки нет: обычное возобновление, трек не меняем. */
    PLAY,

    /** Ошибка, есть следующий трек: продолжаем со следующего. */
    NEXT,

    /** Ошибка, переходить некуда или включён repeat-one: текущий с начала. */
    RESTART_CURRENT,
}

object PlaybackErrorRecoveryPolicy {
    fun actionFor(
        hasError: Boolean,
        hasNext: Boolean,
        repeatOne: Boolean,
    ): PlaybackErrorAction = when {
        !hasError -> PlaybackErrorAction.PLAY
        repeatOne -> PlaybackErrorAction.RESTART_CURRENT
        hasNext -> PlaybackErrorAction.NEXT
        else -> PlaybackErrorAction.RESTART_CURRENT
    }
}
