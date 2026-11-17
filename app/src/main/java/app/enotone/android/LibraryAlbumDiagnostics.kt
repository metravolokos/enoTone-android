package app.enotone.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Only numbers from the last rendered album. No tags, keys, paths or account data. */
internal data class LibraryAlbumAudit(
    val rows: Int,
    val headerTracks: Int,
    val duplicateIdGroups: Int,
)

internal object LibraryAlbumDiagnostics {
    private val mutable = MutableStateFlow<LibraryAlbumAudit?>(null)
    val last = mutable.asStateFlow()

    fun record(state: LibraryUiState) {
        if (state.loading || state.failed || state.section != app.enotone.library.LibraryPage.ALBUMS) return
        val group = state.selectedGroup ?: return
        mutable.value = LibraryAlbumAudit(
            state.visible.size, group.trackIds.size,
            state.visible.groupingBy { it.id }.eachCount().count { it.value > 1 },
        )
    }

    fun clear() { mutable.value = null }
}
