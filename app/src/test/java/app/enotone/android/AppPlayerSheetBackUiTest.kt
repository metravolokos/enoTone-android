package app.enotone.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
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
                    onPlayerExitComplete = { progress.floatValue = 0f },
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
        // Let the zero-progress composition start its asynchronous settle effect, then
        // verify the sheet still begins at the rendered preview position.
        rule.mainClock.advanceTimeByFrame()
        val cancelStart = blueStartAfterRasterSync()
        assertTrue("Cancel must begin continuously: $half → $cancelStart",
            cancelStart in (half - 5)..(half + 5))
        rule.mainClock.advanceTimeBy(100)
        val returning = blueStartAfterRasterSync()
        assertTrue("Cancel must animate towards origin: $cancelStart → $returning",
            returning > rest && returning < cancelStart)
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

    @Test fun committedBackReopensSheetFromBottomAfterExit() {
        val expanded = mutableStateOf(false)
        val progress = mutableFloatStateOf(0f)
        val exitComplete = mutableStateOf(false)
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
                    onPlayerExitComplete = {
                        progress.floatValue = 0f
                        exitComplete.value = true
                    },
                    music = { Box(Modifier.fillMaxSize().background(Color.Red)) },
                    settings = {},
                    player = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
                )
            }
        }
        val height = requireNotNull(host).height
        rule.runOnIdle { expanded.value = true }
        rule.mainClock.advanceTimeBy(50)
        val baseline = blueStartAfterRasterSync()
        rule.mainClock.advanceTimeBy(400)

        rule.runOnIdle { progress.floatValue = 0.5f }
        rule.mainClock.advanceTimeByFrame()
        blueStartAfterRasterSync()

        rule.runOnIdle { expanded.value = false }
        var exitFinished = false
        repeat(24) {
            if (!exitFinished) {
                rule.mainClock.advanceTimeByFrame()
                rule.runOnIdle { exitFinished = exitComplete.value }
            }
        }
        assertTrue("Committed sheet exit callback must run", exitFinished)
        val exited = blueStartAfterRasterSync()
        assertTrue("Committed sheet must finish exiting: $exited / $height", exited >= height - 4)
        rule.runOnIdle { assertEquals(0f, progress.floatValue, 0f) }
        rule.runOnIdle { expanded.value = true }
        rule.mainClock.advanceTimeBy(50)
        val reopened = blueStartAfterRasterSync()
        assertTrue(
            "Reopened sheet should match a normal early frame: $baseline → $reopened",
            reopened in (baseline - 5)..(baseline + 5),
        )
    }

    private class ReopenDuringExitState {
        val expanded = mutableStateOf(true)
        val progress = mutableFloatStateOf(0f)
        val exitComplete = mutableStateOf(false)
        var renderedExpanded = true
    }

    @Test fun reopeningDuringCommitExitClearsBackProgressAndMatchesEntryFrame() {
        val state = ReopenDuringExitState()
        rule.mainClock.autoAdvance = false
        installPairedPlayerRoots(state)
        val height = requireNotNull(host).height
        rule.mainClock.advanceTimeBy(400)

        rule.runOnIdle {
            Snapshot.withMutableSnapshot { state.progress.floatValue = 0.5f }
        }
        rule.mainClock.advanceTimeByFrame()
        val (normalPreview, beforeCommit) = currentPairedStarts()
        assertTrue("Reference sheet should remain at rest: $normalPreview", normalPreview <= 4)
        assertTrue("Preview must be visible before commit: $beforeCommit / $height",
            beforeCommit.toFloat() / height in 0.40f..0.60f)

        closeDuringOneExitFrame(state, beforeCommit, height)
        reopenAndCompareWithReference(state, height)
    }

    private fun installPairedPlayerRoots(state: ReopenDuringExitState) {
        rule.setContent {
            val current = LocalView.current
            val expandedForFrame = state.expanded.value
            val progressForFrame = state.progress.floatValue
            SideEffect {
                host = current
                state.renderedExpanded = expandedForFrame
            }
            EnoToneTheme {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        AppMotionRoot(
                            inSettings = false,
                            diagnostics = false,
                            playerExpanded = expandedForFrame,
                            playerBackProgress = 0f,
                            music = { Box(Modifier.fillMaxSize().background(Color.Red)) },
                            settings = {},
                            player = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        AppMotionRoot(
                            inSettings = false,
                            diagnostics = false,
                            playerExpanded = expandedForFrame,
                            playerBackProgress = progressForFrame,
                            onPlayerExitComplete = {
                                Snapshot.withMutableSnapshot {
                                    state.progress.floatValue = 0f
                                    state.exitComplete.value = true
                                }
                            },
                            music = { Box(Modifier.fillMaxSize().background(Color.Red)) },
                            settings = {},
                            player = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
                        )
                    }
                }
            }
        }
    }

    private fun closeDuringOneExitFrame(
        state: ReopenDuringExitState,
        beforeCommit: Int,
        height: Int,
    ) {
        rule.runOnIdle {
            Snapshot.withMutableSnapshot { state.expanded.value = false }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals("Close target must be applied before reopening", false, state.renderedExpanded)
            assertTrue("Exit callback must still be pending", !state.exitComplete.value)
        }
        val (normalExit, subjectExit) = currentPairedStarts()
        assertTrue("Reference remains visible during its exit: $normalExit", normalExit in 0 until height)
        assertTrue(
            "Subject sheet must remain in its exit path: $beforeCommit → $subjectExit / $height",
            subjectExit in (beforeCommit - 5)..(height - 5),
        )
    }

    private fun reopenAndCompareWithReference(state: ReopenDuringExitState, height: Int) {
        rule.runOnIdle {
            Snapshot.withMutableSnapshot { state.expanded.value = true }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals("Reopen target must be applied", true, state.renderedExpanded)
        }
        rule.mainClock.advanceTimeBy(50)
        val (normalEarlyFrame, reopened) = blueStartsInSameBitmap()
        var callbackRan = false
        var remainingProgress = -1f
        rule.runOnIdle {
            callbackRan = state.exitComplete.value
            remainingProgress = state.progress.floatValue
        }

        assertTrue("Interrupted exit must finish the Back transition", callbackRan)
        assertEquals("Back progress must be reset on interrupted exit", 0f, remainingProgress, 0f)
        assertTrue(
            "Reopened sheet should match the ordinary early frame: " +
                "normal=$normalEarlyFrame, reopened=$reopened, height=$height",
            reopened in (normalEarlyFrame - 5)..(normalEarlyFrame + 5),
        )
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

    /** Обход по столбцам Bitmap без golden-файлов, все координаты из одного кадра. */
    private fun blueStartsInSameBitmap(): Pair<Int, Int> {
        val width = requireNotNull(host).width
        blueStartsAt(width / 4, width * 3 / 4)
        rule.mainClock.advanceTimeByFrame()
        val starts = blueStartsAt(width / 4, width * 3 / 4)
        return starts[0] to starts[1]
    }

    private fun blueStartsAt(vararg xPositions: Int): List<Int> {
        var results = emptyList<Int>()
        rule.runOnIdle {
            val view = requireNotNull(host)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                results = xPositions.map { x ->
                    (0 until bitmap.height).firstOrNull { y ->
                        val pixel = bitmap.getPixel(x, y)
                        android.graphics.Color.blue(pixel) > 210 &&
                            android.graphics.Color.red(pixel) < 60 &&
                            android.graphics.Color.green(pixel) < 60
                    } ?: bitmap.height
                }
            } finally {
                bitmap.recycle()
            }
        }
        return results
    }

    /** Обход по одному столбцу центральной половины для существующих сценариев. */
    private fun blueStart(): Int = blueStartsAt(requireNotNull(host).width / 2).single()
}
