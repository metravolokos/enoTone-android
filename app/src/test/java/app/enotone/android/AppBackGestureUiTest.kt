package app.enotone.android

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
}
