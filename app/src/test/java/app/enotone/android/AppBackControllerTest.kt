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
        val layerScale = 1f - controller.musicProgress(expanded) * AppMotionSpec.BACK_SCALE_REDUCTION
        assertEquals(1f, layerScale, 0f)
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
}
