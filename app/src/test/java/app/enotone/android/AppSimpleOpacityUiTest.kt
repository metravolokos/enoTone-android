package app.enotone.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * D3.5: реальные пиксели Android Compose View в памяти, без файлов Roborazzi и золотых PNG.
 * На границе сдвига возможен антиалиасинг одного пикселя — проверяем центр поверхности.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class AppSimpleOpacityUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun playerSheetFramesNeverBlendTwoHalfTransparentScreens() {
        val expanded = mutableStateOf(false)
        var hostView: View? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val view = LocalView.current
            SideEffect { hostView = view }
            EnoToneTheme {
                AppMotionRoot(
                    inSettings = false,
                    diagnostics = false,
                    playerExpanded = expanded.value,
                    music = { Box(Modifier.fillMaxSize().background(Color.Red)) },
                    settings = {},
                    player = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { expanded.value = true }
        repeat(7) { frame ->
            rule.mainClock.advanceTimeBy(40)
            rule.runOnIdle {
                val host = requireNotNull(hostView)
                assertTrue("Compose View must be measured", host.width > 0 && host.height > 0)
                val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
                try {
                    host.draw(Canvas(bitmap))
                    val pixel = bitmap.getPixel(host.width / 2, host.height * 3 / 4)
                    val red = AndroidColor.red(pixel)
                    val green = AndroidColor.green(pixel)
                    val blue = AndroidColor.blue(pixel)
                    val alpha = AndroidColor.alpha(pixel)
                    assertTrue("Frame $frame: opaque canvas expected, ARGB=${Integer.toHexString(pixel)}",
                        alpha >= 250)
                    val solidRed = red > 220 && blue < 40 && green < 40
                    val solidBlue = blue > 220 && red < 40 && green < 40
                    assertTrue("Frame $frame: no red/blue blending, ARGB=${Integer.toHexString(pixel)}",
                        solidRed || solidBlue)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }
}
