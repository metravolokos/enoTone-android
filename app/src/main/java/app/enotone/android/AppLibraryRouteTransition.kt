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

/** Активная страница получает liveState, уходящая — immutable snapshot. */
@Composable
internal fun AppLibraryRouteTransition(
    nav: AppNavState,
    liveState: LibraryUiState?,
    snapshots: AppLibraryRouteSnapshots,
    searchRegistry: AppSearchBackRegistry?,
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
            CompositionLocalProvider(
                LocalAppSearchBackRegistry provides searchRegistry,
                LocalActiveAppRoute provides appRouteKey(route).takeIf { active },
            ) {
                content(route, snapshot, active)
            }
        }
    }
}
