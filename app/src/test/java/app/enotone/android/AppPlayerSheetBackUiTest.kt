package app.enotone.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
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
 * Реальный AppMotionRoot: верхний край синей шторки измеряется по Bitmap View,
 * а не по boundsInRoot, которые не обязаны учитывать graphicsLayer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class AppPlayerSheetBackUiTest {
    @get:Rule val rule = createComposeRule()
    private var host: View? = null

    @Test fun previewCancelAndCommitKeepContinuousSheetOffset() {
        val expanded = mutableStateOf(true)
        val progress = mutableFloatStateOf(0f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val current = LocalView.current
            SideEffect { host = current }
            EnoToneTheme {
                AppMotionRoot(
                    inSettings = false,
                    diagnostics = false,
                    playerExpanded = expanded.value,
                    playerBackProgress = progress.floatValue,
                    music = { Box(Modifier.fillMaxSize().background(Color.Red)) },
                    settings = {},
                    player = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
                )
            }
        }
        rule.mainClock.advanceTimeBy(400)
        val rest = blueStart()
        val height = requireNotNull(host).height
        assertTrue("Sheet must cover music at rest: $rest", rest <= 4)

        rule.runOnIdle { progress.floatValue = 0.5f }
        rule.mainClock.advanceTimeByFrame()
        val half = blueStartAfterRasterSync()
        assertTrue("Preview must reveal music at about half height: $half / $height",
            half.toFloat() / height in 0.40f..0.60f)

        rule.runOnIdle { progress.floatValue = 0f }
        rule.mainClock.advanceTimeBy(100)
        val returning = blueStartAfterRasterSync()
        assertTrue("Cancel must animate towards origin: $half → $returning",
            returning > rest && returning < half)
        rule.mainClock.advanceTimeBy(250)
        val restored = blueStartAfterRasterSync()
        assertTrue("Cancel restores origin: $rest → $restored", restored <= rest + 5)

        rule.runOnIdle { progress.floatValue = 0.5f }
        rule.mainClock.advanceTimeByFrame()
        val beforeCommit = blueStartAfterRasterSync()
        rule.runOnIdle { expanded.value = false }
        rule.mainClock.advanceTimeByFrame()
        val firstExit = blueStartAfterRasterSync()
        assertTrue("Commit must not jump: $beforeCommit → $firstExit",
            firstExit >= beforeCommit - 5 && firstExit < beforeCommit + height * 0.1f)
        rule.mainClock.advanceTimeBy(110)
        val laterExit = blueStartAfterRasterSync()
        assertTrue("Exit must keep moving down: $firstExit → $laterExit", laterExit > firstExit)
        rule.mainClock.advanceTimeBy(240)
        assertTrue("Sheet must disappear after exit", blueStart() >= height - 4)
    }

    /**
     * Robolectric native raster может отстать на один кадр от layout после смены состояния
     * (CI #904: сначала 0, на следующем кадре 426/852). Считываем следующий
     * фактически нарисованный кадр, не подменяя результат позицией semantics.
     */
    private fun blueStartAfterRasterSync(): Int {
        blueStart() // заставляем Compose/View отрисовать текущее состояние
        rule.mainClock.advanceTimeByFrame()
        return blueStart()
    }

    /** Обход по одному столбцу Bitmap без golden-файлов, только реально нарисованные кадры. */
    private fun blueStart(): Int {
        var result = -1
        rule.runOnIdle {
            val view = requireNotNull(host)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val x = view.width / 2
                result = (0 until bitmap.height).firstOrNull { y ->
                    val pixel = bitmap.getPixel(x, y)
                    android.graphics.Color.blue(pixel) > 210 &&
                        android.graphics.Color.red(pixel) < 60 &&
                        android.graphics.Color.green(pixel) < 60
                } ?: bitmap.height
            } finally {
                bitmap.recycle()
            }
        }
        return result
    }
}
