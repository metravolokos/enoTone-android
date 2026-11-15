package app.enotone.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
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

    private data class CompletedPreview(val target: AppBackTarget, val progress: Float)

    // Старый маршрут продолжает exit даже после начала следующего жеста.
    private val outgoing = mutableStateMapOf<AppRouteKey, CompletedPreview>()
    private var committed = false

    fun preview(target: AppBackTarget, nav: AppNavState, value: Float) {
        val route = appRouteKey(nav)
        if (previewTarget == null || committed || previewRoute != route || previewTarget != target) {
            previewTarget = target
            previewRoute = route
            committed = false
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
        val route = previewRoute
        if (route != null && previewTarget != null && progress > 0f) {
            outgoing[route] = CompletedPreview(requireNotNull(previewTarget), progress)
        }
        committed = true
        dispatchAppBack(
            target = target,
            closePlayer = closePlayer,
            navigateBack = { AppNavigator.back(nav)?.let(updateNav) },
            closeSearch = closeSearch,
        )
    }

    fun cancel() {
        // Отмена второго жеста не снимает exit уже завершённого первого.
        if (!committed) clearCurrent()
    }

    fun finishTransition() {
        outgoing.clear()
        clearCurrent()
    }

    /** Завершение конкретного exit не должно сбросить новый активный preview. */
    fun finishCommittedRoute(route: AppRouteKey) {
        outgoing.remove(route)
        if (committed && previewRoute == route) clearCurrent()
    }

    private fun clearCurrent() {
        progress = 0f
        previewTarget = null
        previewRoute = null
        committed = false
    }

    fun musicProgressFor(route: AppNavState, playerExpanded: Boolean): Float {
        if (playerExpanded) return 0f
        val key = appRouteKey(route)
        if (previewRoute == key && previewTarget in setOf(AppBackTarget.DETAIL, AppBackTarget.TAB)) {
            return progress
        }
        val completed = outgoing[key]
        return if (completed?.target in setOf(AppBackTarget.DETAIL, AppBackTarget.TAB)) {
            completed?.progress ?: 0f
        } else 0f
    }

    fun musicProgress(playerExpanded: Boolean): Float =
        if (!playerExpanded && previewTarget in setOf(AppBackTarget.DETAIL, AppBackTarget.TAB)) {
            progress
        } else 0f

    fun settingsProgress(playerExpanded: Boolean): Float =
        if (!playerExpanded && previewTarget == AppBackTarget.OVERLAY) progress else 0f

    fun playerProgress(): Float = if (previewTarget == AppBackTarget.PLAYER) progress else 0f
}
