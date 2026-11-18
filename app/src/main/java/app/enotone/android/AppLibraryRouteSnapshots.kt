package app.enotone.android

import androidx.compose.runtime.mutableStateMapOf
import app.enotone.library.LibraryPage

/** Какой маршрут должен рассчитывать LibraryViewModel, включая вложенные детали. */
internal fun AppNavState.libraryTarget(): Pair<LibraryPage, String?>? {
    val page = (tab as? AppTab.Library)?.page
    val detail = details.lastOrNull()
    return when (detail) {
        is LibraryDetail.Album -> LibraryPage.ALBUMS to detail.key
        is LibraryDetail.Artist -> LibraryPage.ARTISTS to detail.key
        is LibraryDetail.AlbumArtist -> LibraryPage.ALBUM_ARTISTS to detail.key
        is LibraryDetail.RecentlyAdded -> null
        null -> page?.let { it to null }
    }
}

internal fun LibraryUiState.matchesRoute(route: AppNavState): Boolean =
    route.libraryTarget()?.let { target -> derivedRoute == target } == true

/**
 * Снимки экрана берутся ДО переключения маршрута общей ViewModel.
 * Во время AnimatedContent уходящий экран получает свой зафиксированный снимок,
 * а входящий — предыдущий снимок этого маршрута, пока новая выборка не готова.
 * В карту не кладём состояние промежуточного расчёта (section уже новый, groups ещё старые).
 */
internal class AppLibraryRouteSnapshots {
    private val snapshots = mutableStateMapOf<AppRouteKey, LibraryUiState>()

    fun capture(route: AppNavState, state: LibraryUiState) {
        if (state.matchesRoute(route)) snapshots[appRouteKey(route)] = state
    }

    fun stateFor(route: AppNavState, active: AppNavState, live: LibraryUiState?): LibraryUiState? {
        // Home и другие маршруты вне библиотеки не должны вызывать pendingRoute.
        val target = route.libraryTarget()
        val key = appRouteKey(route)
        return if (target == null) null
        else if (key == appRouteKey(active) && live?.derivedRoute == target) live
        else snapshots[key] ?: live?.pendingRoute(target)
    }
}

/** Не показываем заголовок/песни другого исполнителя, пока входящий маршрут рассчитывается. */
private fun LibraryUiState.pendingRoute(target: Pair<LibraryPage, String?>): LibraryUiState {
    return copy(
        section = target.first,
        selectedGroupKey = target.second,
        derivedRoute = null,
        search = "",
        selectedGroup = null,
        groups = emptyList(),
        shownGroups = emptyList(),
        visible = emptyList(),
        albumTracks = emptyList(),
        loading = true,
    )
}
