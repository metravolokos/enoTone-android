package app.enotone.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Номер ревизии локальной медиатеки в памяти процесса (внешний аудит 2026-11-13, п. 2).
 * Растёт после записи в Room, которую открытые экраны должны увидеть без пересоздания:
 * импорт списка, завершение индексации тегов и подготовки обложек, обновление тегов альбома.
 * Только число — без email, путей и тегов.
 */
internal object LibraryDataRevision {
    private val mutable = MutableStateFlow(0L)
    val value: StateFlow<Long> = mutable.asStateFlow()

    fun bump() = mutable.update { it + 1 }

    /** Переводит переходы фоновых служб в ревизии; запускается один раз в Application. */
    fun watch(
        scope: CoroutineScope,
        indexing: StateFlow<ManagedScanStatus> = ManagedScanRuntime.status,
        artwork: StateFlow<ArtworkScanStatus> = ArtworkScanRuntime.status,
        onChange: () -> Unit = ::bump,
    ): Job = scope.launch {
        launch {
            var previous = indexing.value
            indexing.collect { status ->
                if (LibraryRefreshPolicy.indexingChanged(previous, status)) onChange()
                previous = status
            }
        }
        launch {
            var previous = artwork.value
            artwork.collect { status ->
                if (LibraryRefreshPolicy.artworkChanged(previous, status)) onChange()
                previous = status
            }
        }
    }
}

/** Когда снимок медиатеки устарел из-за фоновой работы. Чистая функция для JUnit. */
internal object LibraryRefreshPolicy {
    /** Во время длинной индексации перечитываем каталог не чаще, чем раз в столько прочитанных треков. */
    const val PROGRESS_STEP = 500

    fun indexingChanged(previous: ManagedScanStatus, current: ManagedScanStatus): Boolean {
        val finished = previous.active && !current.active
        val before = previous.progress?.savedWithTags ?: 0
        val after = current.progress?.savedWithTags ?: 0
        val stepped = current.state == ManagedScanState.RUNNING && after / PROGRESS_STEP > before / PROGRESS_STEP
        return finished || stepped
    }

    fun artworkChanged(previous: ArtworkScanStatus, current: ArtworkScanStatus): Boolean =
        previous.active && !current.active
}
