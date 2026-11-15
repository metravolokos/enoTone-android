package app.enotone.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf

/** Единственный порядок системного Back, независимо от порядка composition отдельных экранов. */
internal enum class AppBackTarget { PLAYER, OVERLAY, DETAIL, SEARCH, TAB, EXIT }

internal fun appBackTarget(
    playerExpanded: Boolean,
    nav: AppNavState,
    searchOpen: Boolean,
): AppBackTarget = when {
    playerExpanded -> AppBackTarget.PLAYER
    nav.overlay != null -> AppBackTarget.OVERLAY
    nav.details.isNotEmpty() -> AppBackTarget.DETAIL
    searchOpen -> AppBackTarget.SEARCH
    nav.tab != AppTab.Home -> AppBackTarget.TAB
    else -> AppBackTarget.EXIT
}

/** Вызывается обработчиком системного жеста и экранными кнопками возврата. */
internal fun dispatchAppBack(
    target: AppBackTarget,
    closePlayer: () -> Unit,
    navigateBack: () -> Unit,
    closeSearch: () -> Unit,
) {
    when (target) {
        AppBackTarget.PLAYER -> closePlayer()
        AppBackTarget.OVERLAY, AppBackTarget.DETAIL, AppBackTarget.TAB -> navigateBack()
        AppBackTarget.SEARCH -> closeSearch()
        AppBackTarget.EXIT -> Unit // Android обрабатывает выход, не подменяем Activity.
    }
}

internal typealias AppRouteKey = Pair<AppTab, List<LibraryDetail>>

/**
 * Регистрация доступна только активному маршруту. Уходящий AnimatedContent
 * не может перехватить Back и не может снять обработчик вновь появившегося маршрута.
 */
internal class AppSearchBackRegistry {
    private val handlers = mutableStateMapOf<AppRouteKey, () -> Unit>()

    fun handler(key: AppRouteKey): (() -> Unit)? = handlers[key]

    fun register(key: AppRouteKey, close: () -> Unit): () -> Unit {
        handlers[key] = close
        return { if (handlers[key] === close) handlers.remove(key) }
    }
}

internal val LocalAppSearchBackRegistry = compositionLocalOf<AppSearchBackRegistry?> { null }
internal val LocalActiveAppRoute = compositionLocalOf<AppRouteKey?> { null }
