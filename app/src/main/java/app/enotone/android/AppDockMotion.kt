package app.enotone.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag

/**
 * Реальный док AppMusicShell. Свайп и predictive Back с целью PLAYER ведут обе
 * панели за жестом; отмена и commit завершаются за существующие 250 мс.
 * Анимация входа очереди и scope общего artwork остаются прежними.
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
    // Движение пальца без задержки; отмена/commit и открытие — плавное завершение.
    val reveal by animateFloatAsState(
        targetValue = targetReveal,
        animationSpec = if (playerExpanded && gestureProgress > 0f) snap()
            else tween(AppMotionSpec.PLAYER_CLOSE_MS),
        label = "dockGestureReveal",
    )
    AnimatedVisibility(
        visible = miniVisible,
        modifier = Modifier.graphicsLayer {
            translationY = -size.height * (1f - reveal) / AppMotionSpec.MINI_UP_DENOMINATOR
            alpha = reveal
        }.testTag("app-mini-dock"),
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
    AnimatedVisibility(
        visible = true,
        modifier = Modifier.graphicsLayer {
            translationY = size.height * (1f - reveal)
        }.testTag("app-bottom-dock"),
        enter = slideInVertically(tween(AppMotionSpec.PLAYER_CLOSE_MS), initialOffsetY = { it }),
        exit = slideOutVertically(tween(AppMotionSpec.PLAYER_CLOSE_MS), targetOffsetY = { it }),
    ) { bottomBar() }
}
