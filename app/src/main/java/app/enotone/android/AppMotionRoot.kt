package app.enotone.android

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
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
    backProgress: Float = 0f,
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
                    Box(
                        Modifier.fillMaxSize()
                            .then(if (playerExpanded) Modifier.clearAndSetSemantics {} else Modifier)
                            .graphicsLayer {
                            if (backProgress > 0f && !playerExpanded) {
                                scaleX = 1f - backProgress * AppMotionSpec.BACK_SCALE_REDUCTION
                                scaleY = 1f - backProgress * AppMotionSpec.BACK_SCALE_REDUCTION
                                translationX = size.width * backProgress * AppMotionSpec.BACK_TRANSLATION_RATIO
                                shape = RoundedCornerShape(AppMotionSpec.BACK_RADIUS_DP.dp * backProgress)
                                clip = true
                            }
                        },
                    ) {
                        AnimatedContent(
                            targetState = diagnostics,
                            transitionSpec = { settingsMotionTransition() },
                            label = "settingsDiagnosticsRoute",
                        ) { showingDiagnostics ->
                            settings(showingDiagnostics)
                        }
                    }
                } else {
                    // Compose-содержимое остаётся под shared-element, но TalkBack не видит его.
                    Box(Modifier.fillMaxSize().then(
                        if (playerExpanded) Modifier.clearAndSetSemantics {} else Modifier,
                    )) {
                        music()
                    }
                }
            }
            AnimatedVisibility(
                visible = playerExpanded,
                enter = slideInVertically(
                    tween(AppMotionSpec.PLAYER_OPEN_MS, easing = AppMotionSpec.emphasizedDecelerate),
                    initialOffsetY = { it },
                ) + fadeIn(tween(AppMotionSpec.PLAYER_OPEN_MS)),
                exit = slideOutVertically(
                    tween(AppMotionSpec.PLAYER_CLOSE_MS, easing = AppMotionSpec.emphasizedAccelerate),
                    targetOffsetY = { it },
                ) + fadeOut(tween(AppMotionSpec.PLAYER_CLOSE_MS)),
                modifier = Modifier.fillMaxSize(),
            ) {
                CompositionLocalProvider(LocalEnoArtworkVisibility provides this) {
                    player()
                }
            }
        }
    }
}
