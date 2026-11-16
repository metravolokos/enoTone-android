package app.enotone.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import app.enotone.library.LibraryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * D3.5: покадровая растеризация настоящих route-контейнеров в Bitmap View.
 * Цветные Box — только содержимое маршрутов; анимации настоящие, Roborazzi не используется.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class AppMotionFrameUiTest {
    @get:Rule val rule = createComposeRule()

    private enum class Ink { RED, BLUE, GREEN }

    private data class Frame(
        val left: Int?,
        val right: Int?,
        val width: Int,
    ) {
        val paintedWidth: Int get() =
            if (left == null || right == null) 0 else right - left + 1
    }

    private fun inspect(view: View, entering: Ink, leaving: Ink): Frame {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            val row = view.height / 2
            val painted = mutableListOf<Int>()
            for (x in 0 until view.width) {
                val pixel = bitmap.getPixel(x, row)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                val a = android.graphics.Color.alpha(pixel)
                assertTrue("Each frame must be opaque, x=$x", a >= 250)
                val old = tinted(leaving, r, g, b)
                val new = tinted(entering, r, g, b)
                assertTrue("No double-tinted route surfaces at x=$x: ${Integer.toHexString(pixel)}",
                    !(old && new))
                if (new) painted.add(x)
            }
            return Frame(painted.firstOrNull(), painted.lastOrNull(), view.width)
        } finally {
            bitmap.recycle()
        }
    }

    private fun tinted(color: Ink, r: Int, g: Int, b: Int): Boolean = when (color) {
        Ink.RED -> r > g + 15 && r > b + 15
        Ink.BLUE -> b > r + 15 && b > g + 15
        Ink.GREEN -> g > r + 15 && g > b + 15
    }

    private fun observeTransition(
        view: View,
        entering: Ink,
        leaving: Ink,
    ) {
        val visibleWidths = mutableListOf<Int>()
        repeat(13) {
            rule.mainClock.advanceTimeBy(20)
            rule.runOnIdle {
                val frame = inspect(view, entering, leaving)
                if (frame.paintedWidth > 0) {
                    val left = requireNotNull(frame.left)
                    val right = requireNotNull(frame.right)
                    assertTrue("No horizontal displacement at frame $it: $left/$right",
                        kotlin.math.abs(left - (frame.width - 1 - right)) <= 3)
                    visibleWidths.add(frame.paintedWidth)
                }
            }
        }
        assertTrue("Incoming route must become visible", visibleWidths.isNotEmpty())
        val first = visibleWidths.first()
        val last = visibleWidths.last()
        assertTrue("Incoming content must start scaled near 0.96: $first → $last",
            first.toFloat() / last in 0.94f..0.99f)
        assertTrue("Incoming scale must settle to full width: $last", last > 0)
    }

    @Test fun actualLibraryDetailUsesCenteredScaledFadeThroughWithoutMixedFrames() {
        val root = AppNavState(tab = AppTab.Library(LibraryPage.ARTISTS))
        val detail = AppNavigator.push(root, LibraryDetail.Artist("fixture"))
        val nav = mutableStateOf(root)
        var host: View? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val view = LocalView.current
            SideEffect { host = view }
            EnoToneTheme {
                AppLibraryRouteTransition(
                    nav = nav.value,
                    liveState = null,
                    snapshots = androidx.compose.runtime.remember { AppLibraryRouteSnapshots() },
                    searchRegistry = null,
                ) { route, _, _ ->
                    Box(Modifier.fillMaxSize().background(
                        if (route.details.isEmpty()) Color.Red else Color.Blue))
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { nav.value = detail }
        observeTransition(requireNotNull(host), Ink.BLUE, Ink.RED)
    }

    @Test fun actualSettingsAndDiagnosticsUseSameCenteredScaleAndOpaqueFrames() {
        val settings = mutableStateOf(false)
        val diagnostics = mutableStateOf(false)
        var host: View? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val view = LocalView.current
            SideEffect { host = view }
            EnoToneTheme {
                AppMotionRoot(
                    inSettings = settings.value,
                    diagnostics = diagnostics.value,
                    playerExpanded = false,
                    music = { Box(Modifier.fillMaxSize().background(Color.Red)) },
                    settings = { diag ->
                        Box(Modifier.fillMaxSize().background(if (diag) Color.Green else Color.Blue))
                    },
                    player = {},
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { settings.value = true }
        observeTransition(requireNotNull(host), Ink.BLUE, Ink.RED)
        rule.runOnIdle { diagnostics.value = true }
        observeTransition(requireNotNull(host), Ink.GREEN, Ink.BLUE)
    }

    @Test fun tenRapidPlayerAndSettingsCyclesReturnToStableOpaqueFrames() {
        val expanded = mutableStateOf(false)
        val inSettings = mutableStateOf(false)
        var host: View? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val view = LocalView.current
            SideEffect { host = view }
            EnoToneTheme {
                AppMotionRoot(
                    inSettings = inSettings.value,
                    diagnostics = false,
                    playerExpanded = expanded.value,
                    music = { Box(Modifier.fillMaxSize().background(Color.Red)) },
                    settings = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
                    player = { Box(Modifier.fillMaxSize().background(Color.Green)) },
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        repeat(10) {
            rule.runOnIdle {
                inSettings.value = !inSettings.value
                expanded.value = true
            }
            rule.mainClock.advanceTimeBy(60)
            rule.runOnIdle { expanded.value = false }
            rule.mainClock.advanceTimeBy(400)
            rule.runOnIdle {
                val expected = if (inSettings.value) Ink.BLUE else Ink.RED
                val frame = inspect(requireNotNull(host), expected,
                    if (expected == Ink.RED) Ink.BLUE else Ink.RED)
                assertEquals("Final frame after rapid cycle $it", frame.width, frame.paintedWidth)
            }
        }
    }
}
