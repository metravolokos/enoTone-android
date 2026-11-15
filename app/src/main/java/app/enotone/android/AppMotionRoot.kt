package app.enotone.android

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import app.enotone.android.core.ui.component.LocalEnoArtworkVisibility
import app.enotone.android.core.ui.component.LocalEnoSharedTransition

/**
 * Каркас переходов D3.4. Основной экран не удаляется при открытии полного плеера:
 * иначе теряются исходная обложка shared element и состояние прокрутки медиатеки.
 * Сам вид экранов остаётся неизменным.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AppMotionRoot(
    inSettings: Boolean,
    diagnostics: Boolean,
    playerExpanded: Boolean,
    music: @Composable () -> Unit,
    settings: @Composable (Boolean) -> Unit,
    player: @Composable () -> Unit,
) {
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalEnoSharedTransition provides this) {
            AnimatedContent(
                targetState = inSettings,
                transitionSpec = { settingsMotionTransition() },
                label = "mainSettingsRoute",
                modifier = Modifier.fillMaxSize(),
            ) { settingsVisible ->
                if (settingsVisible) {
                    AnimatedContent(
                        targetState = diagnostics,
                        transitionSpec = { settingsMotionTransition() },
                        label = "settingsDiagnosticsRoute",
                    ) { showingDiagnostics ->
                        settings(showingDiagnostics)
                    }
                } else {
                    music()
                }
            }
            AnimatedVisibility(
                visible = playerExpanded,
                enter = slideInVertically(
                    tween(350, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)),
                    initialOffsetY = { it },
                ) + fadeIn(tween(350)),
                exit = slideOutVertically(
                    tween(250, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)),
                    targetOffsetY = { it },
                ) + fadeOut(tween(250)),
                modifier = Modifier.fillMaxSize(),
            ) {
                CompositionLocalProvider(LocalEnoArtworkVisibility provides this) {
                    player()
                }
            }
        }
    }
}
