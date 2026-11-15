package app.enotone.android

import androidx.compose.animation.core.CubicBezierEasing

/**
 * D3.4: утверждённые токены движения Android v2 (design/docs/MOTION_ANDROID_V2.md).
 * Не добавлять произвольные длительности в Composable: изменение токена требует решения владельца.
 */
internal object AppMotionSpec {
    const val TAB_EXIT_MS = 90
    const val TAB_ENTER_MS = 160
    const val TAB_ENTER_DELAY_MS = 90
    const val TAB_START_SCALE = 0.96f

    const val PAGE_OPEN_MS = 300
    const val PAGE_CLOSE_MS = 250
    const val PAGE_OPEN_FADE_MS = 120
    const val PAGE_CLOSE_FADE_MS = 100
    const val PREVIOUS_ALPHA = 0.85f
    const val FORWARD_SLIDE_NUMERATOR = 3
    const val SLIDE_DENOMINATOR = 10

    const val PLAYER_OPEN_MS = 350
    const val PLAYER_CLOSE_MS = 250
    const val DOCK_FADE_MS = 150
    const val MINI_UP_DENOMINATOR = 3
    const val SNACKBAR_MS = 200

    const val BACK_SCALE_REDUCTION = 0.1f
    const val BACK_TRANSLATION_RATIO = 0.12f
    const val BACK_RADIUS_DP = 28

    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
