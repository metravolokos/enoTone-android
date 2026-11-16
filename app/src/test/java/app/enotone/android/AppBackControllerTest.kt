package app.enotone.android

import app.enotone.library.LibraryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тестируем именно AppBackController/dispatchAppBack, которые вызывает SettingsScreen. */
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
        assertEquals(0f, controller.musicProgress(expanded), 0f)
        controller.commit(target, current, closePlayer = { expanded = false },
            updateNav = { current = it }, closeSearch = { searchClosed++ })
        assertFalse(expanded)
        assertEquals(detail, current)
        assertEquals(0, searchClosed)
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

    @Test fun cancelledPredictiveGestureDoesNotPopAndDetailHasPreview() {
        val controller = AppBackController()
        var current = detail
        val target = appBackTarget(false, current, false)
        assertEquals(AppBackTarget.DETAIL, target)
        controller.preview(target, current, 0.5f)
        assertEquals(0.5f, controller.musicProgress(false), 0f)
        assertTrue(controller.previewRoute == appRouteKey(detail))
        controller.cancel()
        assertEquals(detail, current)
        assertEquals(0f, controller.progress, 0f)
        assertNull(controller.previewTarget)
        assertNull(controller.previewRoute)
    }

    @Test fun outgoingRouteKeepsProgressUntilTransitionIsFinished() {
        val controller = AppBackController()
        var current = detail
        controller.preview(AppBackTarget.DETAIL, current, 0.5f)
        val outgoing = current
        controller.commit(AppBackTarget.DETAIL, current, closePlayer = {},
            updateNav = { current = it }, closeSearch = {})
        assertEquals(AppNavigator.pop(detail), current)
        assertEquals(0.5f, controller.progress, 0f)
        assertEquals(0.5f,
            appMusicPreviewProgress(false, controller.previewRoute, outgoing, controller.progress), 0f)
        assertEquals(0f,
            appMusicPreviewProgress(false, controller.previewRoute, current, controller.progress), 0f)
        controller.finishTransition()
        assertEquals(0f, controller.progress, 0f)
    }

    @Test fun previousExitRemainsUntilItsTimerWhileSecondGestureIsActive() {
        val controller = AppBackController()
        val first = AppNavigator.push(detail, LibraryDetail.Album("album"))
        controller.preview(AppBackTarget.DETAIL, first, 0.8f)
        var current = first
        controller.commit(AppBackTarget.DETAIL, current, closePlayer = {},
            updateNav = { current = it }, closeSearch = {})
        controller.preview(AppBackTarget.DETAIL, current, 0.5f)
        assertEquals(0.8f, controller.musicProgressFor(first, false), 0f)
        assertEquals(0.5f, controller.musicProgressFor(current, false), 0f)
        controller.cancel()
        assertEquals(0.8f, controller.musicProgressFor(first, false), 0f)
        controller.finishCommittedRoute(appRouteKey(first))
        assertEquals(0f, controller.musicProgressFor(first, false), 0f)
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

    @Test fun secondGestureRetargetsBeforePreviousExitFinishes() {
        val controller = AppBackController()
        val first = AppNavigator.push(detail, LibraryDetail.Album("album"))
        var nav = first
        controller.preview(AppBackTarget.DETAIL, nav, 0.8f)
        controller.commit(AppBackTarget.DETAIL, nav, closePlayer = {},
            updateNav = { nav = it }, closeSearch = {})
        assertEquals(detail, nav)
        // До 250 мс: новый системный жест относится к новому экрану.
        controller.preview(AppBackTarget.DETAIL, nav, 0.5f)
        assertEquals(appRouteKey(nav), controller.previewRoute)
        assertEquals(AppBackTarget.DETAIL, controller.previewTarget)
        assertEquals(0.5f, controller.musicProgress(false), 0f)
        controller.cancel()
        assertEquals(detail, nav)
    }
}
