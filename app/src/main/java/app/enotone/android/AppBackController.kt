package app.enotone.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Один владелец Back. Preview-геометрия сохранена только для полного плеера. */
internal class AppBackController {
    var progress by mutableFloatStateOf(0f)
        private set
    var previewTarget by mutableStateOf<AppBackTarget?>(null)
        private set
    var previewRoute by mutableStateOf<AppRouteKey?>(null)
        private set

    private var playerExitCommitted = false

    fun preview(target: AppBackTarget, nav: AppNavState, value: Float) {
        previewTarget = target
        previewRoute = appRouteKey(nav)
        progress = value.coerceIn(0f, 1f)
        playerExitCommitted = false
    }

    fun commit(
        target: AppBackTarget,
        nav: AppNavState,
        closePlayer: () -> Unit,
        updateNav: (AppNavState) -> Unit,
        closeSearch: () -> Unit,
    ) {
        // Только PLAYER удерживает preview до конца вертикального exit;
        // после pop детали прогресс странице уже не нужен.
        playerExitCommitted = target == AppBackTarget.PLAYER &&
            previewTarget == AppBackTarget.PLAYER && progress > 0f
        dispatchAppBack(
            target = target,
            closePlayer = closePlayer,
            navigateBack = { AppNavigator.back(nav)?.let(updateNav) },
            closeSearch = closeSearch,
        )
        if (!playerExitCommitted) clearCurrent()
    }

    fun cancel() {
        if (!playerExitCommitted) clearCurrent()
    }

    fun finishTransition() = clearCurrent()

    private fun clearCurrent() {
        progress = 0f
        previewTarget = null
        previewRoute = null
        playerExitCommitted = false
    }

    fun playerProgress(): Float = if (previewTarget == AppBackTarget.PLAYER) progress else 0f
}
