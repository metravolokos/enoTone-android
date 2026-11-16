package app.enotone.android

import app.enotone.library.LibraryPage
import org.junit.Assert.assertEquals
import org.junit.Test

/** D3.5: один тип движения для вкладок, деталей и оверлеев. */
class AppMotionTest {
    private val songs = AppTab.Library(LibraryPage.TRACKS)
    private val albums = AppTab.Library(LibraryPage.ALBUMS)

    @Test fun anyNavigationChangeUsesSameTransition() {
        val root = AppNavState(tab = albums)
        val album = AppNavigator.push(root, LibraryDetail.Album("a"))
        val performer = AppNavigator.push(album, LibraryDetail.Artist("r"))
        val author = AppNavigator.push(root, LibraryDetail.AlbumArtist("u"))
        val settings = AppNavigator.openSettings(root)
        val diagnostics = AppNavigator.openDiagnostics(settings)
        val changed = AppMotionRoute.CHANGE
        listOf(
            AppNavState(tab = songs) to root,
            root to album, album to performer, performer to album,
            root to author, author to root, root to settings, settings to diagnostics,
            diagnostics to settings, settings to root,
        ).forEach { (before, after) ->
            assertEquals("Uniform page transition for $before → $after",
                changed, appMotionRoute(before, after))
        }
    }

    @Test fun sameRouteAndScrollRequestDoNotReanimate() {
        val root = AppNavState(tab = songs)
        val repeat = AppNavigator.select(root, songs)
        assertEquals(AppMotionRoute.STAY, appMotionRoute(root, repeat))
        assertEquals(AppMotionRoute.STAY, appMotionRoute(root, root))
        assertEquals(appRouteKey(root), appRouteKey(repeat))
    }
}
