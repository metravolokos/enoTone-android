package app.enotone.android

import app.enotone.library.LibraryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Реальная последовательность AppBackController → dispatchAppBack → AppNavigator. */
class AppBackControllerTest {
    private val tab = AppTab.Library(LibraryPage.ARTISTS)
    private val artist = LibraryDetail.Artist("artist-1")
    private val detail = AppNavigator.push(AppNavState(tab = tab), artist)

    @Test fun playerOverDetailClosesOnlyPlayerAndDoesNotPop() {
        val controller = AppBackController()
        var expanded = true
        var current = detail
        var searchClosed = 0
        val target = appBackTarget(expanded, current, searchOpen = true)
        assertEquals(AppBackTarget.PLAYER, target)
        controller.preview(target, current, 0.5f)
        assertEquals(0.5f, controller.playerProgress(), 0f)
        controller.commit(target, current, closePlayer = { expanded = false },
            updateNav = { current = it }, closeSearch = { searchClosed++ })
        assertFalse(expanded)
        assertEquals(detail, current)
        assertEquals(0, searchClosed)
        assertEquals(0.5f, controller.playerProgress(), 0f)
        controller.finishTransition()
        assertEquals(0f, controller.progress, 0f)
    }

    @Test fun overlayThenDetailThenSearchThenTabThenExit() {
        val controller = AppBackController()
        var current = AppNavigator.openDiagnostics(AppNavigator.openSettings(detail))
        var searched = 0
        fun dispatch(searchOpen: Boolean): AppBackTarget {
            val target = appBackTarget(false, current, searchOpen)
            controller.commit(target, current, closePlayer = {},
                updateNav = { current = it }, closeSearch = { searched++ })
            return target
        }
        assertEquals(AppBackTarget.OVERLAY, dispatch(true))
        assertEquals(AppOverlay.SETTINGS, current.overlay)
        assertEquals(AppBackTarget.OVERLAY, dispatch(true))
        assertNull(current.overlay)
        assertEquals(AppBackTarget.DETAIL, dispatch(true))
        assertEquals(emptyList<LibraryDetail>(), current.details)
        assertEquals(AppBackTarget.SEARCH, dispatch(true))
        assertEquals(1, searched)
        assertEquals(tab, current.tab)
        assertEquals(AppBackTarget.TAB, dispatch(false))
        assertEquals(AppTab.Home, current.tab)
        assertEquals(AppBackTarget.EXIT, dispatch(false))
        assertEquals(AppTab.Home, current.tab)
    }

    @Test fun cancelledPredictiveGestureDoesNotPop() {
        val controller = AppBackController()
        var current = detail
        val target = appBackTarget(false, current, false)
        assertEquals(AppBackTarget.DETAIL, target)
        controller.preview(target, current, 0.5f)
        assertEquals(0.5f, controller.progress, 0f)
        assertEquals(appRouteKey(detail), controller.previewRoute)
        controller.cancel()
        assertEquals(detail, current)
        assertEquals(0f, controller.progress, 0f)
        assertNull(controller.previewTarget)
        assertNull(controller.previewRoute)
        assertEquals(0f, controller.playerProgress(), 0f)
    }

    @Test fun committedDetailBackReleasesPreviewImmediately() {
        val controller = AppBackController()
        var current = detail
        controller.preview(AppBackTarget.DETAIL, current, 0.5f)
        controller.commit(AppBackTarget.DETAIL, current, closePlayer = {},
            updateNav = { current = it }, closeSearch = {})
        assertEquals(AppNavigator.pop(detail), current)
        assertEquals(0f, controller.progress, 0f)
        assertNull(controller.previewTarget)
    }

    @Test fun nextGestureTargetsNewRouteBeforePreviousTransitionFinishes() {
        val controller = AppBackController()
        val first = AppNavigator.push(detail, LibraryDetail.Album("album"))
        var current = first
        controller.preview(AppBackTarget.DETAIL, current, 0.8f)
        controller.commit(AppBackTarget.DETAIL, current, closePlayer = {},
            updateNav = { current = it }, closeSearch = {})
        assertEquals(detail, current)
        controller.preview(AppBackTarget.DETAIL, current, 0.5f)
        assertEquals(0.5f, controller.progress, 0f)
        assertEquals(appRouteKey(detail), controller.previewRoute)
        controller.cancel()
        assertEquals(detail, current)
    }

    @Test fun oldAnimatedRouteCannotUnregisterNewSearchCallback() {
        val registry = AppSearchBackRegistry()
        val key = appRouteKey(detail)
        var calls = 0
        val unregisterOld = registry.register(key) { calls++ }
        val unregisterNew = registry.register(key) { calls += 10 }
        unregisterOld()
        registry.handler(key)?.invoke()
        assertEquals(10, calls)
        unregisterNew()
        assertNull(registry.handler(key))
    }

    @Test fun playerProgressIsOnlyKeptForPlayerExitAndClearedOnCancel() {
        val controller = AppBackController()
        controller.preview(AppBackTarget.PLAYER, detail, 0.65f)
        assertEquals(0.65f, controller.playerProgress(), 0f)
        controller.cancel()
        assertEquals(0f, controller.playerProgress(), 0f)
        controller.preview(AppBackTarget.PLAYER, detail, 0.65f)
        var closed = false
        controller.commit(AppBackTarget.PLAYER, detail, closePlayer = { closed = true },
            updateNav = {}, closeSearch = {})
        assertTrue(closed)
        assertEquals(0.65f, controller.playerProgress(), 0f)
        controller.finishTransition()
        assertEquals(0f, controller.playerProgress(), 0f)
    }
}
