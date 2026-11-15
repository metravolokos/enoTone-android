package app.enotone.android

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/** Движение — только для изменения маршрута. Счётчик scrollToTop не анимируется. */
internal enum class AppMotionRoute { TAB, FORWARD, BACK, STAY }

internal fun appMotionRoute(from: AppNavState, to: AppNavState): AppMotionRoute = when {
    from.tab != to.tab -> AppMotionRoute.TAB
    from.details == to.details -> AppMotionRoute.STAY
    to.details.size > from.details.size -> AppMotionRoute.FORWARD
    else -> AppMotionRoute.BACK
}

internal fun appRouteKey(state: AppNavState): Pair<AppTab, List<LibraryDetail>> =
    state.tab to state.details

private val entering = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val exiting = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal fun AnimatedContentTransitionScope<AppNavState>.appMotionTransition(): ContentTransform =
    when (appMotionRoute(initialState, targetState)) {
        AppMotionRoute.TAB ->
            (fadeIn(tween(160, delayMillis = 90, easing = standard)) +
                scaleIn(tween(160, delayMillis = 90, easing = standard), initialScale = 0.96f))
                .togetherWith(fadeOut(tween(90, easing = standard)))
        AppMotionRoute.FORWARD ->
            (slideInHorizontally(tween(300, easing = entering), initialOffsetX = { it * 3 / 10 }) +
                fadeIn(tween(120, easing = entering)))
                .togetherWith(
                    slideOutHorizontally(tween(300, easing = entering), targetOffsetX = { -it / 10 }) +
                        fadeOut(tween(300, easing = entering), targetAlpha = 0.85f),
                )
        AppMotionRoute.BACK ->
            (slideInHorizontally(tween(250, easing = exiting), initialOffsetX = { -it / 10 }) +
                fadeIn(tween(100, easing = exiting)))
                .togetherWith(
                    slideOutHorizontally(tween(250, easing = exiting), targetOffsetX = { it * 3 / 10 }) +
                        fadeOut(tween(250, easing = exiting), targetAlpha = 0.85f),
                )
        AppMotionRoute.STAY -> fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
    }

internal fun AnimatedContentTransitionScope<Boolean>.settingsMotionTransition(): ContentTransform =
    if (targetState) {
        (slideInHorizontally(tween(300, easing = entering), initialOffsetX = { it * 3 / 10 }) +
            fadeIn(tween(120, easing = entering)))
            .togetherWith(slideOutHorizontally(tween(300, easing = entering), targetOffsetX = { -it / 10 }))
    } else {
        (slideInHorizontally(tween(250, easing = exiting), initialOffsetX = { -it / 10 }) +
            fadeIn(tween(100, easing = exiting)))
            .togetherWith(slideOutHorizontally(tween(250, easing = exiting), targetOffsetX = { it * 3 / 10 }))
    }
