package app.enotone.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.enotone.android.core.ui.component.EnoBottomBar
import app.enotone.android.core.ui.component.EnoBottomBarItem
import app.enotone.android.core.ui.component.EnoIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * R-2: настоящее дерево SharedTransitionLayout + AppDockMotion +
 * EnoToneMiniPlayer + NowPlayingScreen. Снимок промежуточного кадра,
 * а не проверка наличия текста/ключа sharedBounds.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class AppSharedArtworkMotionUiTest {
    @get:Rule val rule = createComposeRule()
    private var renderView: View? = null

    private data class Cover(val width: Int, val components: Int)

    @Test fun oneArtworkFliesBetweenMiniAndFullDuringOpenAndClose() {
        val expanded = mutableStateOf(false)
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply {
            eraseColor(AndroidColor.GREEN)
        }
        val playback = PlaybackSnapshot(
            connected = true, queueTotal = 1, title = "Artwork transition",
            currentTrackId = "cover-id", playbackState = "PLAYING",
        )
        val player = SharedPlayerUiState(playback = playback, artwork = bitmap)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val view = LocalView.current
            SideEffect { renderView = view }
            EnoToneTheme {
                AppMotionRoot(
                    inSettings = false, diagnostics = false, playerExpanded = expanded.value,
                    music = {
                        Box(Modifier.fillMaxSize()) {
                            Column(
                                Modifier.align(Alignment.BottomCenter)
                                    .fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AppDockMotion(
                                    playerExpanded = expanded.value,
                                    miniVisible = true, dragProgress = 0f, backProgress = 0f,
                                    miniPlayer = {
                                        EnoToneMiniPlayer(
                                            player = player, onOpen = {}, onPlayPause = {},
                                            onNext = {},
                                        )
                                    },
                                    bottomBar = {
                                        EnoBottomBar(
                                            items = listOf(
                                                EnoBottomBarItem(EnoIcons.HOME, "Home"),
                                            ),
                                            selectedIndex = 0, onSelect = {},
                                        )
                                    },
                                )
                            }
                        }
                    },
                    settings = {},
                    player = {
                        NowPlayingScreen(
                            trackId = "cover-id", title = "Artwork transition",
                            artist = "Artist", album = "Album", artwork = bitmap,
                            playbackState = "PLAYING", errorCode = 0,
                            positionMs = 100L, durationMs = 1_000L,
                            canSeek = true, controlsEnabled = true,
                            hasPrevious = false, hasNext = false,
                            queuePosition = 1, queueTotal = 1,
                            repeatMode = Player.REPEAT_MODE_OFF, shuffleEnabled = false,
                            onClose = { expanded.value = false },
                            onSeekFraction = {}, onPlayPause = {}, onPrevious = {},
                            onNext = {}, onCycleRepeat = {}, onToggleShuffle = {},
                        )
                    },
                )
            }
        }
        rule.mainClock.advanceTimeBy(450)
        val mini = cover()
        assertEquals(1, mini.components)

        rule.runOnIdle { expanded.value = true }
        rule.mainClock.advanceTimeBy(AppMotionSpec.PLAYER_OPEN_MS.toLong() / 2)
        val opening = cover()
        rule.mainClock.advanceTimeBy(450)
        val full = cover()
        assertEquals(1, full.components)

        rule.runOnIdle { expanded.value = false }
        rule.mainClock.advanceTimeBy(AppMotionSpec.PLAYER_CLOSE_MS.toLong() / 2)
        val closing = cover()
        rule.mainClock.advanceTimeBy(450)
        val miniAgain = cover()

        assertTrue("Full artwork must be larger than mini", full.width > mini.width * 2)
        assertIntermediate("opening", mini, opening, full)
        assertIntermediate("closing", mini, closing, full)
        assertEquals(1, miniAgain.components)
        assertEquals(mini.width, miniAgain.width)
    }

    private fun assertIntermediate(
        phase: String, mini: Cover, intermediate: Cover, full: Cover,
    ) {
        assertEquals("$phase must paint one visible cover, not two", 1, intermediate.components)
        assertTrue("$phase must be wider than mini: $mini → $intermediate",
            intermediate.width > mini.width)
        assertTrue("$phase must be narrower than full: $intermediate → $full",
            intermediate.width < full.width)
    }

    /**
     * Distinct solid green bitmap isolates the actual rendered artwork pixels
     * from Graphite/Porcelain backgrounds and player controls.
     * Component count catches simultaneous separated source + destination covers.
     */
    private fun cover(): Cover {
        rule.mainClock.advanceTimeByFrame()
        lateinit var captured: Bitmap
        rule.runOnIdle {
            val view = requireNotNull(renderView)
            check(view.width > 0 && view.height > 0) { "Compose view not measured" }
            captured = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(captured))
        }
        val width = captured.width
        val height = captured.height
        val marked = BooleanArray(width * height)
        var minX = width
        var maxX = -1
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = captured.getPixel(x, y)
                val red = (pixel ushr 16) and 255
                val green = (pixel ushr 8) and 255
                val blue = pixel and 255
                if (green > 217 && red < 38 && blue < 38) {
                    marked[y * width + x] = true
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    count++
                }
            }
        }
        assertTrue("Expected visible lime artwork pixels", count > 100)
        val stack = IntArray(width * height)
        var components = 0
        for (index in marked.indices) {
            if (!marked[index]) continue
            components++
            var front = 0
            var end = 1
            stack[0] = index
            marked[index] = false
            while (front < end) {
                val cell = stack[front++]
                val x = cell % width
                val y = cell / width
                fun visit(neighbor: Int) {
                    if (marked[neighbor]) {
                        marked[neighbor] = false
                        stack[end++] = neighbor
                    }
                }
                if (x > 0) visit(cell - 1)
                if (x < width - 1) visit(cell + 1)
                if (y > 0) visit(cell - width)
                if (y < height - 1) visit(cell + width)
            }
        }
        return Cover(maxX - minX + 1, components)
    }
}
