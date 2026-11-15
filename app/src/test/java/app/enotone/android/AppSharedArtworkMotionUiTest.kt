package app.enotone.android

import android.graphics.Bitmap
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.enotone.android.core.ui.component.EnoBottomBar
import app.enotone.android.core.ui.component.EnoBottomBarItem
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.android.core.ui.component.LocalEnoArtworkVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Проверяет фактические bounds production shared artwork в настоящем
 * SharedTransitionLayout: мини-плеер → полный плеер → мини-плеер.
 * Каждый кадр перехода читается по семантическому узлу с тем же shared key.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class AppSharedArtworkMotionUiTest {
    @get:Rule val rule = createComposeRule()
    private var densityScale = 1f

    private data class Cover(val sharedBoundsWidthsDp: List<Float>) {
        val widthDp: Float get() = sharedBoundsWidthsDp.maxOrNull() ?: 0f
    }

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
            val density = LocalDensity.current.density
            SideEffect { densityScale = density }
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
                                        androidx.compose.runtime.CompositionLocalProvider(
                                            LocalEnoArtworkVisibility provides this,
                                        ) {
                                            EnoToneMiniPlayer(
                                                player = player, onOpen = {}, onPlayPause = {},
                                                onNext = {},
                                            )
                                        }
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
        rule.waitForIdle()
        val mini = cover()
        assertEquals(1, mini.sharedBoundsWidthsDp.size)
        assertEquals(44f, mini.widthDp, 1f)

        rule.runOnIdle { expanded.value = true }
        val opening = awaitIntermediate("opening", mini.widthDp, FULL_ARTWORK_DP)
        rule.mainClock.advanceTimeBy(450)
        rule.waitForIdle()
        val full = cover()
        assertEquals(1, full.sharedBoundsWidthsDp.size)
        assertEquals(FULL_ARTWORK_DP, full.widthDp, 2f)

        rule.runOnIdle { expanded.value = false }
        val closing = awaitIntermediate("closing", mini.widthDp, full.widthDp)
        rule.mainClock.advanceTimeBy(450)
        rule.waitForIdle()
        val miniAgain = cover()

        assertEquals(1, miniAgain.sharedBoundsWidthsDp.size)
        assertEquals(mini.widthDp, miniAgain.widthDp, 1f)
        assertTrue(
            "Opening must pass through an intermediate size; sampled " + opening.sharedBoundsWidthsDp,
            opening.widthDp > mini.widthDp && opening.widthDp < full.widthDp,
        )
        assertTrue(
            "Closing must pass through an intermediate size; sampled " + closing.sharedBoundsWidthsDp,
            closing.widthDp > mini.widthDp && closing.widthDp < full.widthDp,
        )
    }

    private fun awaitIntermediate(phase: String, miniWidthDp: Float, fullWidthDp: Float): Cover {
        val samples = mutableListOf<List<Float>>()
        repeat(MAX_TRANSITION_FRAMES) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            val sample = cover()
            samples += sample.sharedBoundsWidthsDp
            if (sample.sharedBoundsWidthsDp.any { it > miniWidthDp && it < fullWidthDp }) {
                println("ARTWORK_PROGRESS phase=" + phase + " frames=" + samples)
                return sample
            }
        }
        println("ARTWORK_PROGRESS phase=" + phase + " frames=" + samples)
        throw AssertionError(
            phase + " did not expose intermediate shared bounds between " +
                miniWidthDp + "dp and " + fullWidthDp + "dp",
        )
    }

    private fun cover(): Cover {
        val widthsDp = rule.onAllNodesWithTag(
            "eno-shared-artwork", useUnmergedTree = true,
        ).fetchSemanticsNodes().map { it.boundsInRoot.width / densityScale }
        return Cover(widthsDp)
    }

    private companion object {
        const val FULL_ARTWORK_DP = 320f
        const val MAX_TRANSITION_FRAMES = 36
    }
}
