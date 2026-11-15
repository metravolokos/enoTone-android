package app.enotone.android

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
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

private fun forwardOffset(width: Int) =
    width * AppMotionSpec.FORWARD_SLIDE_NUMERATOR / AppMotionSpec.SLIDE_DENOMINATOR

private fun previousOffset(width: Int) = -width / AppMotionSpec.SLIDE_DENOMINATOR

internal fun AnimatedContentTransitionScope<AppNavState>.appMotionTransition(): ContentTransform =
    when (appMotionRoute(initialState, targetState)) {
        AppMotionRoute.TAB -> (
            fadeIn(tween(
                AppMotionSpec.TAB_ENTER_MS,
                delayMillis = AppMotionSpec.TAB_ENTER_DELAY_MS,
                easing = AppMotionSpec.standard,
            )) + scaleIn(
                tween(
                    AppMotionSpec.TAB_ENTER_MS,
                    delayMillis = AppMotionSpec.TAB_ENTER_DELAY_MS,
                    easing = AppMotionSpec.standard,
                ),
                initialScale = AppMotionSpec.TAB_START_SCALE,
            )
        ).togetherWith(fadeOut(tween(AppMotionSpec.TAB_EXIT_MS, easing = AppMotionSpec.standard)))
        AppMotionRoute.FORWARD -> (
            slideInHorizontally(
                animationSpec = tween(AppMotionSpec.PAGE_OPEN_MS, easing = AppMotionSpec.emphasizedDecelerate),
                initialOffsetX = ::forwardOffset,
            ) + fadeIn(tween(AppMotionSpec.PAGE_OPEN_FADE_MS, easing = AppMotionSpec.emphasizedDecelerate))
        ).togetherWith(
            slideOutHorizontally(
                animationSpec = tween(AppMotionSpec.PAGE_OPEN_MS, easing = AppMotionSpec.emphasizedDecelerate),
                targetOffsetX = ::previousOffset,
            ) + fadeOut(
                animationSpec = tween(AppMotionSpec.PAGE_OPEN_MS, easing = AppMotionSpec.emphasizedDecelerate),
                targetAlpha = AppMotionSpec.PREVIOUS_ALPHA,
            ),
        )
        AppMotionRoute.BACK -> (
            slideInHorizontally(
                animationSpec = tween(AppMotionSpec.PAGE_CLOSE_MS, easing = AppMotionSpec.emphasizedAccelerate),
                initialOffsetX = ::previousOffset,
            ) + fadeIn(tween(AppMotionSpec.PAGE_CLOSE_FADE_MS, easing = AppMotionSpec.emphasizedAccelerate))
        ).togetherWith(
            slideOutHorizontally(
                animationSpec = tween(AppMotionSpec.PAGE_CLOSE_MS, easing = AppMotionSpec.emphasizedAccelerate),
                targetOffsetX = ::forwardOffset,
            ) + fadeOut(
                animationSpec = tween(AppMotionSpec.PAGE_CLOSE_MS, easing = AppMotionSpec.emphasizedAccelerate),
                targetAlpha = AppMotionSpec.PREVIOUS_ALPHA,
            ),
        )
        AppMotionRoute.STAY -> fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
    }

internal fun AnimatedContentTransitionScope<Boolean>.settingsMotionTransition(): ContentTransform =
    if (targetState) {
        (
            slideInHorizontally(
                animationSpec = tween(AppMotionSpec.PAGE_OPEN_MS, easing = AppMotionSpec.emphasizedDecelerate),
                initialOffsetX = ::forwardOffset,
            ) + fadeIn(tween(AppMotionSpec.PAGE_OPEN_FADE_MS, easing = AppMotionSpec.emphasizedDecelerate))
        ).togetherWith(
            slideOutHorizontally(
                animationSpec = tween(AppMotionSpec.PAGE_OPEN_MS, easing = AppMotionSpec.emphasizedDecelerate),
                targetOffsetX = ::previousOffset,
            ),
        )
    } else {
        (
            slideInHorizontally(
                animationSpec = tween(AppMotionSpec.PAGE_CLOSE_MS, easing = AppMotionSpec.emphasizedAccelerate),
                initialOffsetX = ::previousOffset,
            ) + fadeIn(tween(AppMotionSpec.PAGE_CLOSE_FADE_MS, easing = AppMotionSpec.emphasizedAccelerate))
        ).togetherWith(
            slideOutHorizontally(
                animationSpec = tween(AppMotionSpec.PAGE_CLOSE_MS, easing = AppMotionSpec.emphasizedAccelerate),
                targetOffsetX = ::forwardOffset,
            ),
        )
    }
