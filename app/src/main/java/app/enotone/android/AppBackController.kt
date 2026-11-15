package app.enotone.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Один источник прогресса и команд Back для SettingsScreen и Compose UI тестов.
 * При commit состояние сохраняется на уходящем маршруте до finishTransition().
 */
internal class AppBackController {
    var progress by mutableFloatStateOf(0f)
        private set
    var previewTarget by mutableStateOf<AppBackTarget?>(null)
        private set
    var previewRoute by mutableStateOf<AppRouteKey?>(null)
        private set

    fun preview(target: AppBackTarget, nav: AppNavState, value: Float) {
        if (previewTarget == null) {
            previewTarget = target
            previewRoute = appRouteKey(nav)
        }
        progress = value.coerceIn(0f, 1f)
    }

    fun commit(
        target: AppBackTarget,
        nav: AppNavState,
        closePlayer: () -> Unit,
        updateNav: (AppNavState) -> Unit,
        closeSearch: () -> Unit,
    ) {
        dispatchAppBack(
            target = target,
            closePlayer = closePlayer,
            navigateBack = { AppNavigator.back(nav)?.let(updateNav) },
            closeSearch = closeSearch,
        )
    }

    fun cancel() = finishTransition()

    fun finishTransition() {
        progress = 0f
        previewTarget = null
        previewRoute = null
    }

    fun musicProgress(playerExpanded: Boolean): Float =
        if (!playerExpanded && previewTarget in setOf(AppBackTarget.DETAIL, AppBackTarget.TAB)) {
            progress
        } else 0f

    fun settingsProgress(playerExpanded: Boolean): Float =
        if (!playerExpanded && previewTarget == AppBackTarget.OVERLAY) progress else 0f

    fun playerProgress(): Float = if (previewTarget == AppBackTarget.PLAYER) progress else 0f
}
