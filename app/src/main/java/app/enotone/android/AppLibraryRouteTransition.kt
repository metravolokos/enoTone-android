package app.enotone.android

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics

/** D3.5: predictive Back только управляет навигацией, геометрия страницы не меняется. */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun AppRouteBackLayer(
    route: AppNavState,
    playerExpanded: Boolean,
    previewRoute: AppRouteKey?,
    progress: Float,
    resolvedProgress: Float? = null,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().semantics { appBackScale = 1f }) { content() }
}

/** Неактивная страница получает свой frozen snapshot и не может перехватить поиск/Back. */
@Composable
internal fun AppLibraryRouteTransition(
    nav: AppNavState,
    liveState: LibraryUiState?,
    snapshots: AppLibraryRouteSnapshots,
    playerExpanded: Boolean,
    backProgress: Float,
    backPreviewRoute: AppRouteKey?,
    searchRegistry: AppSearchBackRegistry?,
    previewForRoute: ((AppNavState) -> Float)? = null,
    content: @Composable (route: AppNavState, state: LibraryUiState?, active: Boolean) -> Unit,
) {
    AnimatedContent(
        targetState = nav,
        contentKey = ::appRouteKey,
        transitionSpec = { appMotionTransition() },
        label = "musicRoutes",
        modifier = Modifier.fillMaxSize(),
    ) { route ->
        val active = appRouteKey(route) == appRouteKey(nav)
        val snapshot = snapshots.stateFor(route, nav, liveState)
        if (active && liveState != null) {
            SideEffect { snapshots.capture(route, liveState) }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AppRouteBackLayer(
                route = route,
                playerExpanded = playerExpanded,
                previewRoute = backPreviewRoute,
                progress = backProgress,
                resolvedProgress = previewForRoute?.invoke(route),
            ) {
                CompositionLocalProvider(
                    LocalAppSearchBackRegistry provides searchRegistry,
                    LocalActiveAppRoute provides appRouteKey(route).takeIf { active },
                ) {
                    content(route, snapshot, active)
                }
            }
        }
    }
}
