package app.enotone.android

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith

/** D3.5: переход выбирается только по факту смены маршрута. */
internal enum class AppMotionRoute { CHANGE, STAY }

internal fun appMotionRoute(from: AppNavState, to: AppNavState): AppMotionRoute =
    if (appRouteKey(from) == appRouteKey(to) && from.overlay == to.overlay) {
        AppMotionRoute.STAY
    } else {
        AppMotionRoute.CHANGE
    }

internal fun appRouteKey(state: AppNavState): Pair<AppTab, List<LibraryDetail>> =
    state.tab to state.details

/** Единый fade-through: старый экран гаснет за 90 мс, новый через 90 мс растёт 0,96→1. */
internal fun <T> AnimatedContentTransitionScope<T>.appPageTransition(): ContentTransform =
    (
        fadeIn(tween(
            AppMotionSpec.PAGE_ENTER_MS,
            delayMillis = AppMotionSpec.PAGE_ENTER_DELAY_MS,
            easing = AppMotionSpec.standard,
        )) + scaleIn(
            tween(
                AppMotionSpec.PAGE_ENTER_MS,
                delayMillis = AppMotionSpec.PAGE_ENTER_DELAY_MS,
                easing = AppMotionSpec.standard,
            ),
            initialScale = AppMotionSpec.PAGE_START_SCALE,
        )
    ).togetherWith(fadeOut(tween(AppMotionSpec.PAGE_EXIT_MS, easing = AppMotionSpec.standard)))

internal fun AnimatedContentTransitionScope<AppNavState>.appMotionTransition(): ContentTransform =
    if (appMotionRoute(initialState, targetState) == AppMotionRoute.STAY) {
        fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
    } else {
        appPageTransition()
    }
