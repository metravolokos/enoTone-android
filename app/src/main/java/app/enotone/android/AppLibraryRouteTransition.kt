package app.enotone.android

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Реально применяемый слой predictive back, наблюдаемый из Compose-теста. */
@Composable
internal fun AppRouteBackLayer(
    route: AppNavState,
    playerExpanded: Boolean,
    previewRoute: AppRouteKey?,
    progress: Float,
    resolvedProgress: Float? = null,
    content: @Composable () -> Unit,
) {
    val visualProgress = resolvedProgress ?: appMusicPreviewProgress(playerExpanded, previewRoute, route, progress)
    Box(
        Modifier.fillMaxSize()
            .semantics { appBackScale = 1f - visualProgress * AppMotionSpec.BACK_SCALE_REDUCTION }
            .graphicsLayer {
                if (visualProgress > 0f) {
                    scaleX = 1f - visualProgress * AppMotionSpec.BACK_SCALE_REDUCTION
                    scaleY = 1f - visualProgress * AppMotionSpec.BACK_SCALE_REDUCTION
                    translationX = size.width * visualProgress * AppMotionSpec.BACK_TRANSLATION_RATIO
                    shape = RoundedCornerShape(AppMotionSpec.BACK_RADIUS_DP.dp * visualProgress)
                    clip = true
                }
            },
    ) { content() }
}

/**
 * Переход получает неизменяемые route-снимки; только активный child может
 * запустить onRouteSelected. Уходящая страница не подписана на новые поля выбора.
 */
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
