package app.enotone.android

import app.enotone.library.LibraryPage
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Нижняя панель Android v2 (ADR-0086). */
internal sealed interface AppTab {
    data object Home : AppTab
    data class Library(val page: LibraryPage) : AppTab
}

/** Детали открываются поверх исходной вкладки, не меняя её выбор. */
internal sealed interface LibraryDetail {
    val key: String
    data class Album(override val key: String) : LibraryDetail
    data class Artist(override val key: String) : LibraryDetail
}

internal enum class AppOverlay { SETTINGS, DIAGNOSTICS }

internal data class AppNavState(
    val tab: AppTab = AppTab.Home,
    val overlay: AppOverlay? = null,
    val scrollToTop: Int = 0,
    val details: List<LibraryDetail> = emptyList(),
)

/**
 * Чистая навигация. Каждый detail хранит ключ из OfflineLibraryGrouping, а не название;
 * для любого перехода на экране можно вернуться к прежней вкладке и позиции прокрутки.
 */
internal object AppNavigator {
    fun select(state: AppNavState, tab: AppTab): AppNavState =
        if (state.overlay == null && state.tab == tab) {
            state.copy(details = emptyList(), scrollToTop = state.scrollToTop + 1)
        } else {
            state.copy(tab = tab, overlay = null, details = emptyList())
        }

    fun push(state: AppNavState, detail: LibraryDetail): AppNavState {
        if (state.tab !is AppTab.Library || state.overlay != null || detail.key.isBlank()) return state
        if (state.details.lastOrNull() == detail) return state
        return state.copy(details = (state.details + detail).takeLast(MAX_DETAILS))
    }

    fun pop(state: AppNavState): AppNavState =
        if (state.details.isEmpty()) state else state.copy(details = state.details.dropLast(1))

    fun openSettings(state: AppNavState): AppNavState = state.copy(overlay = AppOverlay.SETTINGS)

    fun openDiagnostics(state: AppNavState): AppNavState = state.copy(overlay = AppOverlay.DIAGNOSTICS)

    /** null означает, что «Назад» передаётся Android. */
    fun back(state: AppNavState): AppNavState? = when {
        state.overlay == AppOverlay.DIAGNOSTICS -> state.copy(overlay = AppOverlay.SETTINGS)
        state.overlay == AppOverlay.SETTINGS -> state.copy(overlay = null)
        state.details.isNotEmpty() -> pop(state)
        state.tab != AppTab.Home -> state.copy(tab = AppTab.Home)
        else -> null
    }

    fun onVisiblePages(state: AppNavState, visible: List<LibraryPage>): AppNavState {
        val tab = state.tab
        return if (tab is AppTab.Library && tab.page !in visible) {
            state.copy(tab = AppTab.Home, details = emptyList())
        } else state
    }

    fun onSignedOut(state: AppNavState): AppNavState =
        state.copy(tab = AppTab.Home, overlay = null, details = emptyList())

    /** Четвёртое поле добавлено к прежнему формату, первые три поля не изменились. */
    fun save(state: AppNavState): String {
        val tab = when (val value = state.tab) {
            AppTab.Home -> HOME
            is AppTab.Library -> value.page.name
        }
        val details = state.details.joinToString(DETAIL_SEPARATOR) { detail ->
            val kind = if (detail is LibraryDetail.Album) ALBUM else ARTIST
            kind + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(detail.key.toByteArray(StandardCharsets.UTF_8))
        }
        return listOf(tab, state.overlay?.name.orEmpty(), state.scrollToTop.toString(), details)
            .joinToString(SEPARATOR)
    }

    fun restore(saved: String): AppNavState {
        val parts = saved.split(SEPARATOR)
        val tab = parts.getOrNull(0)
            ?.let { name -> LibraryPage.entries.firstOrNull { it.name == name } }
            ?.let { AppTab.Library(it) }
            ?: AppTab.Home
        val overlay = parts.getOrNull(1)?.let { name ->
            AppOverlay.entries.firstOrNull { it.name == name }
        }
        val scroll = parts.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val details = if (tab is AppTab.Library) {
            parts.getOrNull(3).orEmpty().split(DETAIL_SEPARATOR).mapNotNull(::parseDetail)
                .takeLast(MAX_DETAILS)
        } else emptyList()
        return AppNavState(tab, overlay, scroll, details)
    }

    private fun parseDetail(item: String): LibraryDetail? {
        if (item.length < 2) return null
        val key = try {
            String(Base64.getUrlDecoder().decode(item.drop(1)), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (key.isBlank()) return null
        return when (item.first()) {
            ALBUM -> LibraryDetail.Album(key)
            ARTIST -> LibraryDetail.Artist(key)
            else -> null
        }
    }

    private const val HOME = "HOME"
    private const val SEPARATOR = "|"
    private const val DETAIL_SEPARATOR = ";"
    private const val ALBUM = 'a'
    private const val ARTIST = 'r'
    private const val MAX_DETAILS = 8
}
