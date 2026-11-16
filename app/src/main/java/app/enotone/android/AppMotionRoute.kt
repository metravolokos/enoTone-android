package app.enotone.android

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith

/** Смена вкладок и вложенных экранов использует одну мягкую смену без горизонтальных сдвигов. */
internal enum class AppMotionRoute { TAB, FORWARD, BACK, STAY }

internal fun appMotionRoute(from: AppNavState, to: AppNavState): AppMotionRoute = when {
    from.tab != to.tab -> AppMotionRoute.TAB
    from.details == to.details -> AppMotionRoute.STAY
    to.details.size > from.details.size -> AppMotionRoute.FORWARD
    else -> AppMotionRoute.BACK
}

internal fun appRouteKey(state: AppNavState): Pair<AppTab, List<LibraryDetail>> =
    state.tab to state.details

private fun <T> AnimatedContentTransitionScope<T>.fadeThrough(scaleTab: Boolean): ContentTransform {
    val appearance = fadeIn(tween(
        AppMotionSpec.TAB_ENTER_MS,
        delayMillis = AppMotionSpec.TAB_ENTER_DELAY_MS,
        easing = AppMotionSpec.standard,
    ))
    val enter = if (scaleTab) appearance + scaleIn(
        tween(
            AppMotionSpec.TAB_ENTER_MS,
            delayMillis = AppMotionSpec.TAB_ENTER_DELAY_MS,
            easing = AppMotionSpec.standard,
        ),
        initialScale = AppMotionSpec.TAB_START_SCALE,
    ) else appearance
    return enter.togetherWith(fadeOut(tween(AppMotionSpec.TAB_EXIT_MS, easing = AppMotionSpec.standard)))
}

internal fun AnimatedContentTransitionScope<AppNavState>.appMotionTransition(): ContentTransform =
    when (appMotionRoute(initialState, targetState)) {
        AppMotionRoute.STAY -> fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
        AppMotionRoute.TAB -> fadeThrough(scaleTab = true)
        AppMotionRoute.FORWARD, AppMotionRoute.BACK -> fadeThrough(scaleTab = false)
    }

internal fun AnimatedContentTransitionScope<Boolean>.settingsMotionTransition(): ContentTransform =
    fadeThrough(scaleTab = false)
