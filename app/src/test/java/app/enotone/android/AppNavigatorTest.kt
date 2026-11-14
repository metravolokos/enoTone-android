package app.enotone.android

import app.enotone.library.LibraryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Переходы каркаса D3 (ADR-0086): вкладки, «Назад», повтор вкладки, скрытые страницы, выход. */
class AppNavigatorTest {
    private val songs = AppTab.Library(LibraryPage.TRACKS)
    private val albums = AppTab.Library(LibraryPage.ALBUMS)

    @Test fun selectingAnotherTabSwitchesWithoutScrollRequest() {
        val state = AppNavigator.select(AppNavState(), songs)
        assertEquals(songs, state.tab)
        assertEquals(0, state.scrollToTop)
    }

    @Test fun reselectingActiveTabRequestsScrollToTopEachTime() {
        var state = AppNavigator.select(AppNavState(), songs)
        state = AppNavigator.select(state, songs)
        state = AppNavigator.select(state, songs)
        assertEquals(songs, state.tab)
        assertEquals(2, state.scrollToTop)
    }

    @Test fun selectingTabFromSettingsClosesSettingsWithoutScrolling() {
        val inSettings = AppNavigator.openSettings(AppNavState(tab = songs))
        val state = AppNavigator.select(inSettings, songs)
        assertNull(state.overlay)
        assertEquals(0, state.scrollToTop)
    }

    @Test fun backWalksDiagnosticsSettingsTabHomeThenExits() {
        var state: AppNavState? = AppNavigator.openDiagnostics(
            AppNavigator.openSettings(AppNavState(tab = albums)),
        )
        state = AppNavigator.back(requireNotNull(state))
        assertEquals(AppOverlay.SETTINGS, state?.overlay)
        state = AppNavigator.back(requireNotNull(state))
        assertEquals(AppNavState(tab = albums), state)
        state = AppNavigator.back(requireNotNull(state))
        assertEquals(AppNavState(tab = AppTab.Home), state)
        assertNull(AppNavigator.back(requireNotNull(state)))
    }

    @Test fun hidingOpenPageReturnsHomeAndKeepsOtherPages() {
        val onAlbums = AppNavState(tab = albums)
        assertEquals(AppTab.Home, AppNavigator.onVisiblePages(onAlbums, listOf(LibraryPage.TRACKS)).tab)
        assertEquals(albums, AppNavigator.onVisiblePages(onAlbums, listOf(LibraryPage.ALBUMS)).tab)
    }

    @Test fun signOutResetsToHome() {
        val state = AppNavigator.openSettings(AppNavState(tab = songs))
        assertEquals(AppNavState(tab = AppTab.Home, overlay = null), AppNavigator.onSignedOut(state))
    }

    @Test fun saveRestoreRoundTripsEveryShapeAndToleratesGarbage() {
        val states = listOf(
            AppNavState(),
            AppNavState(tab = songs, scrollToTop = 3),
            AppNavState(tab = albums, overlay = AppOverlay.DIAGNOSTICS),
            AppNavState(tab = AppTab.Library(LibraryPage.ALBUM_ARTISTS), overlay = AppOverlay.SETTINGS),
        )
        states.forEach { assertEquals(it, AppNavigator.restore(AppNavigator.save(it))) }
        assertEquals(AppNavState(), AppNavigator.restore("garbage"))
    }
}
