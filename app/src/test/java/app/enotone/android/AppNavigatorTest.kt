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

    @Test fun albumArtistAlbumBackFollowsDetailStack() {
        val initial = AppNavState(tab = AppTab.Library(LibraryPage.ARTISTS))
        val artist = AppNavigator.push(initial, LibraryDetail.Artist("artist:one"))
        val album = AppNavigator.push(artist, LibraryDetail.Album("album:first"))
        assertEquals(initial.tab, album.tab)
        assertEquals(artist, AppNavigator.back(album))
        assertEquals(initial, AppNavigator.back(artist))
        assertEquals(AppTab.Home, AppNavigator.back(initial)?.tab)
    }

    @Test fun reselectingSameTabClearsDetailsAndRequestsTop() {
        val initial = AppNavigator.push(AppNavState(tab = songs), LibraryDetail.Album("album:one"))
        val next = AppNavigator.select(initial, songs)
        assertEquals(emptyList<LibraryDetail>(), next.details)
        assertEquals(1, next.scrollToTop)
    }

    @Test fun switchingTabsOrSigningOutClearsDetails() {
        val start = AppNavigator.push(AppNavState(tab = songs), LibraryDetail.Album("a"))
        assertEquals(emptyList<LibraryDetail>(), AppNavigator.select(start, albums).details)
        assertEquals(emptyList<LibraryDetail>(), AppNavigator.onSignedOut(start).details)
    }

    @Test fun pushHasBoundedDepthAndAvoidsIdenticalTop() {
        var state = AppNavState(tab = songs)
        repeat(11) { state = AppNavigator.push(state, LibraryDetail.Album("a:$it")) }
        assertEquals(8, state.details.size)
        assertEquals(LibraryDetail.Album("a:3"), state.details.first())
        assertEquals(state, AppNavigator.push(state, LibraryDetail.Album("a:10")))
    }

    @Test fun newFormatRoundTripsOpaqueKeysAndReadsOldThreeFields() {
        val initial = AppNavState(tab = albums, details = listOf(
            LibraryDetail.Artist("artist:А|Б/В"),
            LibraryDetail.Album("album:Сборник\\u0000Artist"),
        ))
        assertEquals(initial, AppNavigator.restore(AppNavigator.save(initial)))
        assertEquals(AppNavState(tab = albums, scrollToTop = 5),
            AppNavigator.restore("ALBUMS||5"))
        assertEquals(emptyList<LibraryDetail>(),
            AppNavigator.restore("TRACKS||0|zthis-is-not-base64").details)
    }
}
