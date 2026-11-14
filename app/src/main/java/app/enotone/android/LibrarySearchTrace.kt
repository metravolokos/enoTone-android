package app.enotone.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Обезличенный след последнего поиска по песням для «Диагностики» (BUG track search, 2026-11-14).
 * Только длина запроса, путь, числа и класс ошибки — без текста запроса и названий.
 */
internal object LibrarySearchTrace {
    private val mutable = MutableStateFlow("")
    val last: StateFlow<String> = mutable.asStateFlow()

    fun record(line: String) {
        mutable.value = line
    }
}
