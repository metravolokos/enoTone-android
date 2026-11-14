package app.enotone.android

import app.enotone.library.LibraryPage

/** Вкладка нижней панели Android v2: «Домой» или страница медиатеки (ADR-0086). */
internal sealed interface AppTab {
    data object Home : AppTab
    data class Library(val page: LibraryPage) : AppTab
}

/** Экраны поверх вкладок, у которых нет нижней панели и мини-плеера (O-10). */
internal enum class AppOverlay { SETTINGS, DIAGNOSTICS }

/**
 * Состояние навигации каркаса D3. «Сейчас играет» сюда не входит: это слой поверх любого экрана,
 * им управляет PlayerViewModel (#117 — открытие из уведомления).
 * [scrollToTop] — номер запроса «прокрутить в начало» при повторном нажатии на активную вкладку.
 */
internal data class AppNavState(
    val tab: AppTab = AppTab.Home,
    val overlay: AppOverlay? = null,
    val scrollToTop: Int = 0,
)

/**
 * Чистые переходы без Android: проверяются JVM-тестами. Глубина стека намеренно мала —
 * «Настройки» → «Диагностика»; экраны альбома и исполнителя пока живут внутри медиатеки (D4).
 */
internal object AppNavigator {
    fun select(state: AppNavState, tab: AppTab): AppNavState =
        if (state.overlay == null && state.tab == tab) {
            state.copy(scrollToTop = state.scrollToTop + 1)
        } else {
            state.copy(tab = tab, overlay = null)
        }

    fun openSettings(state: AppNavState): AppNavState = state.copy(overlay = AppOverlay.SETTINGS)

    fun openDiagnostics(state: AppNavState): AppNavState = state.copy(overlay = AppOverlay.DIAGNOSTICS)

    /** Системное «Назад»; `null` — стек исчерпан, «Назад» уходит системе (выход из приложения). */
    fun back(state: AppNavState): AppNavState? = when {
        state.overlay == AppOverlay.DIAGNOSTICS -> state.copy(overlay = AppOverlay.SETTINGS)
        state.overlay == AppOverlay.SETTINGS -> state.copy(overlay = null)
        state.tab != AppTab.Home -> state.copy(tab = AppTab.Home)
        else -> null
    }

    /** ADR-0052: скрытая в «Настройках» страница пропадает из панели; если она была открыта — на «Домой». */
    fun onVisiblePages(state: AppNavState, visible: List<LibraryPage>): AppNavState {
        val tab = state.tab
        return if (tab is AppTab.Library && tab.page !in visible) state.copy(tab = AppTab.Home) else state
    }

    /** ADR-0080: после выхода из аккаунта — на «Домой», без экранов настроек в стеке. */
    fun onSignedOut(state: AppNavState): AppNavState = state.copy(tab = AppTab.Home, overlay = null)

    /** Сохранение при повороте и пересоздании Activity — компактной строкой без Parcelable. */
    fun save(state: AppNavState): String {
        val tab = when (val value = state.tab) {
            AppTab.Home -> HOME
            is AppTab.Library -> value.page.name
        }
        return listOf(tab, state.overlay?.name.orEmpty(), state.scrollToTop.toString()).joinToString(SEPARATOR)
    }

    fun restore(saved: String): AppNavState {
        val parts = saved.split(SEPARATOR)
        val tab = parts.getOrNull(0)
            ?.let { name -> LibraryPage.entries.firstOrNull { it.name == name } }
            ?.let { AppTab.Library(it) }
            ?: AppTab.Home
        val overlay = parts.getOrNull(1)?.let { name -> AppOverlay.entries.firstOrNull { it.name == name } }
        val scroll = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return AppNavState(tab, overlay, scroll)
    }

    private const val HOME = "HOME"
    private const val SEPARATOR = "|"
}
