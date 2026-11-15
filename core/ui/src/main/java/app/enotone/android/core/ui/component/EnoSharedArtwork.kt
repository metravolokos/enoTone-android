package app.enotone.android.core.ui.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * D3.4: единый ключ обложки для мини-плеера и полного плеера.
 * Области передаются из :app, компоненты :core:ui и :feature:player не зависят от :app.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalEnoSharedTransition = compositionLocalOf<SharedTransitionScope?> { null }

val LocalEnoArtworkVisibility = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Не меняет размер, обрезку или внешний вид компонента вне перехода. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.enoSharedArtwork(): Modifier {
    val shared = LocalEnoSharedTransition.current ?: return this
    val visibility = LocalEnoArtworkVisibility.current ?: return this
    return with(shared) {
        this@enoSharedArtwork.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "enotone-now-playing-artwork"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ ->
                tween(durationMillis = 350, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))
            },
        )
    }
}
