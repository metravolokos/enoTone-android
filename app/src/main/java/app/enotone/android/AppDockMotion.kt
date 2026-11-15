package app.enotone.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Движение дока, используемое настоящим AppMusicShell.
 * Первый перенос без изменения поведения: тест промежуточного прогресса должен быть красным.
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
    AnimatedVisibility(
        visible = miniVisible && !playerExpanded,
        modifier = Modifier.testTag("app-mini-dock"),
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
        visible = !playerExpanded,
        modifier = Modifier.testTag("app-bottom-dock"),
        enter = slideInVertically(tween(AppMotionSpec.PLAYER_CLOSE_MS), initialOffsetY = { it }),
        exit = slideOutVertically(tween(AppMotionSpec.PLAYER_CLOSE_MS), targetOffsetY = { it }),
    ) { bottomBar() }
}
