package app.enotone.android

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import app.enotone.library.LibraryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Platform BackHandler (Android 13), а не ручной вызов onCommit из тестовой фикстуры. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppBackGestureUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun physicalBackClosesPlayerBeforePoppingArtistDetail() {
        val root = AppNavState(tab = AppTab.Library(LibraryPage.ARTISTS))
        val detail = AppNavigator.push(root, LibraryDetail.Artist("artist"))
        var nav by mutableStateOf(detail)
        var expanded by mutableStateOf(true)
        val controller = AppBackController()
        lateinit var dispatcher: OnBackPressedDispatcher
        rule.setContent {
            val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            SideEffect { dispatcher = owner.onBackPressedDispatcher }
            val target = appBackTarget(expanded, nav, searchOpen = false)
            AppBackGesture(
                enabled = target != AppBackTarget.EXIT,
                onPreview = { controller.preview(target, nav, it) },
                onCommit = {
                    controller.commit(
                        target = target,
                        nav = nav,
                        closePlayer = { expanded = false },
                        updateNav = { nav = it },
                        closeSearch = {},
                    )
                },
                onCancel = controller::cancel,
            )
            Text("Current route")
        }
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.runOnIdle {
            assertFalse(expanded)
            assertEquals(detail, nav)
        }
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.runOnIdle { assertEquals(root, nav) }
    }

    @Test
    @Config(sdk = [35])
    fun nextPredictiveGestureUsesNewRouteBeforePreviousExitCompletes() {
        val root = AppNavState(tab = AppTab.Library(LibraryPage.ARTISTS))
        val artist = AppNavigator.push(root, LibraryDetail.Artist("author"))
        val album = AppNavigator.push(artist, LibraryDetail.Album("album"))
        var nav by mutableStateOf(album)
        val controller = AppBackController()
        lateinit var dispatcher: OnBackPressedDispatcher
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            SideEffect { dispatcher = owner.onBackPressedDispatcher }
            val target = appBackTarget(false, nav, false)
            AppBackGesture(
                enabled = target != AppBackTarget.EXIT,
                onPreview = { controller.preview(target, nav, it) },
                onCommit = {
                    controller.commit(target, nav, closePlayer = {},
                        updateNav = { nav = it }, closeSearch = {})
                },
                onCancel = controller::cancel,
            )
            Text("Active route")
        }
        rule.mainClock.advanceTimeByFrame()
        halfPredictiveBack(dispatcher)
        rule.runOnIdle { assertEquals(appRouteKey(album), controller.previewRoute) }
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(artist, nav) }
        halfPredictiveBack(dispatcher)
        rule.runOnIdle {
            assertEquals(appRouteKey(artist), controller.previewRoute)
            assertEquals(0.5f, controller.musicProgressFor(artist, false), 0f)
            assertEquals(0.5f, controller.musicProgressFor(album, false), 0f)
            dispatcher.dispatchOnBackCancelled()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle {
            assertEquals(artist, nav)
            assertNull(controller.previewRoute)
        }
    }

    private fun halfPredictiveBack(dispatcher: OnBackPressedDispatcher) {
        rule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    @Test
    @Config(sdk = [35])
    fun android14PredictiveBackProgressAndCancellationPreserveDetail() {
        val root = AppNavState(tab = AppTab.Library(LibraryPage.ARTISTS))
        val detail = AppNavigator.push(root, LibraryDetail.Artist("artist"))
        var nav by mutableStateOf(detail)
        val controller = AppBackController()
        lateinit var dispatcher: OnBackPressedDispatcher
        rule.setContent {
            val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            SideEffect { dispatcher = owner.onBackPressedDispatcher }
            val target = appBackTarget(playerExpanded = false, nav = nav, searchOpen = false)
            AppBackGesture(
                enabled = target != AppBackTarget.EXIT,
                onPreview = { controller.preview(target, nav, it) },
                onCommit = {
                    controller.commit(
                        target, nav, closePlayer = {},
                        updateNav = { nav = it }, closeSearch = {},
                    )
                },
                onCancel = controller::cancel,
            )
            Text("Detail is active")
        }
        rule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(AppBackTarget.DETAIL, controller.previewTarget)
            assertEquals(0.5f, controller.musicProgress(false), 0f)
            assertTrue(controller.previewRoute == appRouteKey(detail))
            assertEquals(detail, nav)
            dispatcher.dispatchOnBackCancelled()
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(detail, nav)
            assertEquals(0f, controller.progress, 0f)
            assertEquals(null, controller.previewTarget)
        }
    }
}
