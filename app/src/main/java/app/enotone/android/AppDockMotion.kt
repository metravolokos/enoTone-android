package app.enotone.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import app.enotone.android.core.ui.component.LocalEnoSharedTransition
import kotlin.math.roundToInt

/** The actual placement translation in px, exposed to UI assertions (not an animation flag). */
internal val AppDockOffsetKey = SemanticsPropertyKey<Float>("appDockOffsetPx")
internal var SemanticsPropertyReceiver.appDockOffsetPx by AppDockOffsetKey

/**
 * Shared artwork exits in its own AnimatedVisibilityScope, while the dock
 * moves by layout offset so gesture previews remain proportional.
 * A separate non-shared mini is revealed behind the player during a partial swipe.
 */
@Composable
internal fun AppDockMotion(
    playerExpanded: Boolean,
    miniVisible: Boolean,
    dragProgress: Float,
    backProgress: Float,
    miniPlayer: @Composable AnimatedVisibilityScope.() -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val gestureProgress = maxOf(dragProgress, backProgress).coerceIn(0f, 1f)
    val targetReveal = if (playerExpanded) gestureProgress else 1f
    val settledReveal by animateFloatAsState(
        targetValue = targetReveal,
        animationSpec = if (playerExpanded && gestureProgress > 0f) snap()
            else tween(AppMotionSpec.PLAYER_CLOSE_MS),
        label = "dockGestureReveal",
    )
    // Direct gesture progress must update in the same frame as the pointer or
    // predictive-back callback. Only settling after cancel/commit is animated.
    val reveal = if (playerExpanded && gestureProgress > 0f) gestureProgress else settledReveal
    val miniHeightPx = with(LocalDensity.current) { MINI_HEIGHT.toPx() }
    val miniOffsetPx = -miniHeightPx * (1f - reveal) /
        AppMotionSpec.MINI_UP_DENOMINATOR
    if (miniVisible) {
        Box(
            Modifier.fillMaxWidth().height(MINI_HEIGHT)
                .offset { IntOffset(0, miniOffsetPx.roundToInt()) }
                .graphicsLayer { alpha = reveal }
                .semantics { appDockOffsetPx = miniOffsetPx }
                .testTag("app-mini-dock"),
        ) {
            // This source *exits* when Now Playing opens: sharedBounds needs
            // a departing AnimatedVisibilityScope to animate 44 -> 320 dp.
            AnimatedVisibility(
                visible = !playerExpanded,
                enter = fadeIn(tween(AppMotionSpec.DOCK_FADE_MS)) +
                    slideInVertically(
                        tween(AppMotionSpec.PLAYER_CLOSE_MS),
                        initialOffsetY = { -it / AppMotionSpec.MINI_UP_DENOMINATOR },
                    ),
                exit = fadeOut(tween(AppMotionSpec.DOCK_FADE_MS)) +
                    slideOutVertically(
                        tween(AppMotionSpec.PLAYER_CLOSE_MS, easing = AppMotionSpec.emphasizedAccelerate),
                        targetOffsetY = { -it / AppMotionSpec.MINI_UP_DENOMINATOR },
                    ),
            ) { miniPlayer() }
            // During a partial drag/predictive PLAYER Back the dock can peek out
            // without registering a second source for shared artwork.
            AnimatedVisibility(
                visible = playerExpanded && gestureProgress > 0f,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
            ) {
                CompositionLocalProvider(LocalEnoSharedTransition provides null) {
                    miniPlayer()
                }
            }
        }
    }
    var bottomHeightPx by remember { mutableIntStateOf(0) }
    val bottomOffsetPx = bottomHeightPx * (1f - reveal)
    AnimatedVisibility(
        visible = true,
        modifier = Modifier
            .offset { IntOffset(0, bottomOffsetPx.roundToInt()) }
            .onSizeChanged { bottomHeightPx = it.height }
            .semantics { appDockOffsetPx = bottomOffsetPx }
            .testTag("app-bottom-dock"),
        enter = slideInVertically(tween(AppMotionSpec.PLAYER_CLOSE_MS), initialOffsetY = { it }),
        exit = slideOutVertically(tween(AppMotionSpec.PLAYER_CLOSE_MS), targetOffsetY = { it }),
    ) { bottomBar() }
}
