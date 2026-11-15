package app.enotone.android

import app.enotone.library.LibraryPage
import org.junit.Assert.assertEquals
import org.junit.Test

/** D3.4: выбор кривой зависит от маршрута, а не от случайной рекомпозиции. */
class AppMotionTest {
    private val songs = AppTab.Library(LibraryPage.TRACKS)
    private val albums = AppTab.Library(LibraryPage.ALBUMS)

    @Test fun changesOfTabAreFadeThrough() {
        assertEquals(AppMotionRoute.TAB,
            appMotionRoute(AppNavState(tab = songs), AppNavState(tab = albums)))
    }

    @Test fun nestedDetailsEnterForwardAndLeaveBackwards() {
        val root = AppNavState(tab = albums)
        val album = AppNavigator.push(root, LibraryDetail.Album("a"))
        val performer = AppNavigator.push(album, LibraryDetail.Artist("r"))
        assertEquals(AppMotionRoute.FORWARD, appMotionRoute(root, album))
        assertEquals(AppMotionRoute.FORWARD, appMotionRoute(album, performer))
        assertEquals(AppMotionRoute.BACK, appMotionRoute(performer, album))
        assertEquals(AppMotionRoute.BACK, appMotionRoute(album, root))
    }

    @Test fun scrollRequestAndSameTabDoNotReanimate() {
        val initial = AppNavState(tab = songs)
        val repeat = AppNavigator.select(initial, songs)
        assertEquals(AppMotionRoute.STAY, appMotionRoute(initial, repeat))
        assertEquals(appRouteKey(initial), appRouteKey(repeat))
    }
}
