package app.enotone.android

import androidx.compose.runtime.mutableStateMapOf
import app.enotone.library.LibraryPage

/** Какой маршрут должен рассчитывать LibraryViewModel, включая вложенные детали. */
internal fun AppNavState.libraryTarget(): Pair<LibraryPage, String?>? {
    val tab = tab as? AppTab.Library ?: return null
    val detail = details.lastOrNull()
    return when (detail) {
        is LibraryDetail.Album -> LibraryPage.ALBUMS to detail.key
        is LibraryDetail.Artist -> LibraryPage.ARTISTS to detail.key
        null -> tab.page to null
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
        val key = appRouteKey(route)
        if (key == appRouteKey(active) && live != null && live.matchesRoute(route)) return live
        return snapshots[key] ?: live?.pendingRoute(route)
    }
}

/** Не показываем заголовок/песни другого исполнителя, пока входящий маршрут рассчитывается. */
private fun LibraryUiState.pendingRoute(route: AppNavState): LibraryUiState {
    val target = requireNotNull(route.libraryTarget())
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
