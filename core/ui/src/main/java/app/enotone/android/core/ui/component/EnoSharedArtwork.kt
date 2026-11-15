package app.enotone.android.core.ui.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
    val shared = LocalEnoSharedTransition.current
    val visibility = LocalEnoArtworkVisibility.current
    return if (shared == null || visibility == null) this else with(shared) {
        this@enoSharedArtwork.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "enotone-now-playing-artwork"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ ->
                tween(durationMillis = SHARED_DURATION_MS, easing = sharedEasing)
            },
        )
    }
}

private const val SHARED_DURATION_MS = 350
private val sharedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
