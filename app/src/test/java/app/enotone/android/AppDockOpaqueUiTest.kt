package app.enotone.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** D3.5: док проверяется без перекрывающей шторки, чтобы увидеть его реальные пиксели. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class AppDockOpaqueUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun bothDockSurfacesKeepOpaquePixelsAndFixedGeometryAcrossPlayerStateChanges() {
        val expanded = mutableStateOf(false)
        var view: View? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            EnoToneTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Column {
                        AppDockMotion(
                            miniVisible = true,
                            miniPlayer = {
                                Box(Modifier.fillMaxWidth().height(64.dp).background(Color.Red))
                            },
                            bottomBar = {
                                Box(Modifier.fillMaxWidth().height(70.dp).background(Color.Blue))
                            },
                        )
                    }
                    // Флаг заставляет оболочку перекомпоноваться, но док от него не зависит.
                    if (expanded.value) Box(Modifier.fillMaxWidth().height(1.dp))
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        val mini = bounds("app-mini-dock")
        val bar = bounds("app-bottom-dock")
        repeat(10) { frame ->
            rule.runOnIdle { expanded.value = !expanded.value }
            rule.mainClock.advanceTimeByFrame()
            rule.runOnIdle {
                assertEquals("Mini geometry at frame $frame", mini, bounds("app-mini-dock"))
                assertEquals("Bottom bar geometry at frame $frame", bar, bounds("app-bottom-dock"))
                val host = requireNotNull(view)
                val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
                try {
                    host.draw(Canvas(bitmap))
                    assertOpaqueColor(bitmap, mini, red = true)
                    assertOpaqueColor(bitmap, bar, red = false)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun bounds(tag: String): Rect = rule.onAllNodesWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNodes().single().boundsInRoot

    private fun assertOpaqueColor(bitmap: Bitmap, bounds: Rect, red: Boolean) {
        val x = bounds.center.x.toInt().coerceIn(0, bitmap.width - 1)
        val y = bounds.center.y.toInt().coerceIn(0, bitmap.height - 1)
        val pixel = bitmap.getPixel(x, y)
        assertEquals("Alpha of dock must be 1", 255, android.graphics.Color.alpha(pixel))
        assertTrue("Dock must not fade to the background: ${Integer.toHexString(pixel)}",
            if (red) {
                android.graphics.Color.red(pixel) > 220 && android.graphics.Color.blue(pixel) < 35
            } else {
                android.graphics.Color.blue(pixel) > 220 && android.graphics.Color.red(pixel) < 35
            })
    }
}
