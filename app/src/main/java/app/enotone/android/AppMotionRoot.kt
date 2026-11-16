package app.enotone.android

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * D3.5: только одна непрозрачная поверхность текущего маршрута.
 * Музыка остаётся смонтированной под плеером, чтобы не терять scroll/search.
 * Полный плеер — единая шторка снизу, без shared-element и затухания фона.
 */
@Composable
internal fun AppMotionRoot(
    inSettings: Boolean,
    diagnostics: Boolean,
    playerExpanded: Boolean,
    music: @Composable () -> Unit,
    settings: @Composable (Boolean) -> Unit,
    player: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = inSettings,
            transitionSpec = { settingsMotionTransition() },
            label = "mainSettingsRoute",
            modifier = Modifier.fillMaxSize(),
        ) { settingsVisible ->
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .then(if (playerExpanded) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                if (settingsVisible) {
                    AnimatedContent(
                        targetState = diagnostics,
                        transitionSpec = { settingsMotionTransition() },
                        label = "settingsDiagnosticsRoute",
                        modifier = Modifier.fillMaxSize(),
                    ) { showingDiagnostics ->
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            settings(showingDiagnostics)
                        }
                    }
                } else {
                    music()
                }
            }
        }
        AnimatedVisibility(
            visible = playerExpanded,
            enter = slideInVertically(
                tween(AppMotionSpec.PLAYER_OPEN_MS, easing = AppMotionSpec.emphasizedDecelerate),
                initialOffsetY = { it },
            ),
            exit = slideOutVertically(
                tween(AppMotionSpec.PLAYER_CLOSE_MS, easing = AppMotionSpec.emphasizedAccelerate),
                targetOffsetY = { it },
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                player()
            }
        }
    }
}
