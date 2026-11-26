package app.enotone.android

import app.enotone.library.ManagedIndexProgress
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
        val before = processed(previous.progress)
        val after = processed(current.progress)
        val stepped = current.state == ManagedScanState.RUNNING && after / PROGRESS_STEP > before / PROGRESS_STEP
        // The terminal snapshot is published with terminating = true, so `active` stays true
        // until the service is destroyed. Publish the revision on the transition itself,
        // otherwise an open library misses the finished batch until teardown.
        val terminal = previous.state == ManagedScanState.RUNNING &&
            current.state != ManagedScanState.RUNNING
        return finished || stepped || terminal
    }

    /** Both kinds of successful handling count: a track read without tags still changed the library. */
    private fun processed(progress: ManagedIndexProgress?): Int =
        (progress?.savedWithTags ?: 0) + (progress?.completedWithoutTags ?: 0)

    fun artworkChanged(previous: ArtworkScanStatus, current: ArtworkScanStatus): Boolean =
        previous.active && !current.active
}
