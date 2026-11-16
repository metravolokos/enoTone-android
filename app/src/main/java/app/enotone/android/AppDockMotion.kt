package app.enotone.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * D3.5: один мини-плеер, без пропорционального дока или резервной копии.
 * Шторка всегда перекрывает док; его состояние не зависит от жеста.
 */
@Composable
internal fun AppDockMotion(
    playerExpanded: Boolean,
    miniVisible: Boolean,
    miniPlayer: @Composable AnimatedVisibilityScope.() -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    if (miniVisible) {
        AnimatedVisibility(
            visible = !playerExpanded,
            enter = fadeIn(tween(AppMotionSpec.PLAYER_CLOSE_MS)),
            exit = fadeOut(tween(AppMotionSpec.PLAYER_CLOSE_MS)),
            modifier = Modifier.fillMaxWidth().height(MINI_HEIGHT).testTag("app-mini-dock"),
        ) {
            miniPlayer()
        }
    }
    AnimatedVisibility(
        visible = !playerExpanded,
        enter = fadeIn(tween(AppMotionSpec.PLAYER_CLOSE_MS)),
        exit = fadeOut(tween(AppMotionSpec.PLAYER_CLOSE_MS)),
        modifier = Modifier.testTag("app-bottom-dock"),
    ) {
        bottomBar()
    }
}
