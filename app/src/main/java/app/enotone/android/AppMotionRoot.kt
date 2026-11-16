package app.enotone.android

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.roundToInt

/**
 * Три маршрута используют один appPageTransition; единственный движущийся слой — шторка плеера.
 * Подложка продолжает жить для прокрутки, но скрыта от TalkBack под раскрытым плеером.
 */
@Composable
internal fun AppMotionRoot(
    inSettings: Boolean,
    diagnostics: Boolean,
    playerExpanded: Boolean,
    playerBackProgress: Float = 0f,
    music: @Composable () -> Unit,
    settings: @Composable (Boolean) -> Unit,
    player: @Composable () -> Unit,
) {
    val previewSettle = remember { Animatable(0f) }
    val latestBackProgress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playerBackProgress) {
        if (playerBackProgress > 0f) {
            previewSettle.snapTo(playerBackProgress.coerceIn(0f, 1f))
        } else {
            // LaunchedEffect is asynchronous: snapTo(progress) can be cancelled before it
            // stores the preview value. Reapply the last rendered progress before settling.
            val cancelFrom = latestBackProgress.floatValue.coerceIn(0f, 1f)
            if (cancelFrom > 0f) {
                previewSettle.snapTo(cancelFrom)
            }
            previewSettle.animateTo(0f, animationSpec = tween(AppMotionSpec.PLAYER_CLOSE_MS))
        }
    }
    SideEffect {
        if (playerBackProgress > 0f) {
            latestBackProgress.floatValue = playerBackProgress.coerceIn(0f, 1f)
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = inSettings,
            transitionSpec = { appPageTransition() },
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
                        transitionSpec = { appPageTransition() },
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
                // Preview уже сдвинул шторку; exit проходит лишь оставшийся путь.
                targetOffsetY = { height ->
                    (height * (1f - playerBackProgress.coerceIn(0f, 1f))).roundToInt()
                },
            ),
            // Системный preview сдвигает размещение всей шторки, а не только её RenderNode:
            // это видимо и в реальных кадрах, и для координат ввода/доступности.
            // Animatable читается в placement, поэтому cancel не рекомпозирует плеер по кадрам.
            // The layer block observes Animatable values on every animation frame without
            // recomposing or remeasuring the player subtree.
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val back = if (playerBackProgress > 0f) {
                    playerBackProgress
                } else {
                    previewSettle.value
                }
                translationY = size.height * back
            },
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("app-player-sheet"),
            ) {
                player()
            }
        }
    }
}
