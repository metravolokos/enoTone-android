package app.enotone.android

import androidx.compose.animation.core.CubicBezierEasing

/** D3.5: одни токены для каждой страницы; шторка плеера имеет отдельное вертикальное движение. */
internal object AppMotionSpec {
    const val PAGE_EXIT_MS = 90
    const val PAGE_ENTER_MS = 160
    const val PAGE_ENTER_DELAY_MS = 90
    const val PAGE_START_SCALE = 0.96f

    const val PLAYER_OPEN_MS = 350
    const val PLAYER_CLOSE_MS = 250
    const val SNACKBAR_MS = 200
    const val SEARCH_EXPAND_MS = 250
    const val SEARCH_COLLAPSE_MS = 150

    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
