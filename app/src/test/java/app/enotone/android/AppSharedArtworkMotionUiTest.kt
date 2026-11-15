package app.enotone.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import android.graphics.Color as AndroidColor

/**
 * Проверяет фактические bounds production shared artwork в настоящем
 * SharedTransitionLayout: мини-плеер → полный плеер → мини-плеер.
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
        val player = SharedPlayerUiState(
            playback = PlaybackSnapshot(
                connected = true, queueTotal = 1, title = "Artwork transition",
                currentTrackId = "cover-id", playbackState = "PLAYING",
            ),
            artwork = bitmap,
        )
        rule.mainClock.autoAdvance = false
        rule.setContent { ArtworkMotionFixture(expanded, player, bitmap) }

        settleFrames(INITIAL_SETTLE_MS)
        val mini = cover()
        assertEquals(1, mini.sharedBoundsWidthsDp.size)
        assertEquals(MINI_ARTWORK_DP, mini.widthDp, WIDTH_TOLERANCE_DP)

        rule.runOnIdle { expanded.value = true }
        val opening = awaitIntermediate("opening", mini.widthDp, FULL_ARTWORK_DP)
        settleFrames(TRANSITION_SETTLE_MS)
        val full = cover()
        assertEquals(1, full.sharedBoundsWidthsDp.size)
        assertEquals(FULL_ARTWORK_DP, full.widthDp, WIDTH_TOLERANCE_DP)

        rule.runOnIdle { expanded.value = false }
        val closing = awaitIntermediate("closing", mini.widthDp, full.widthDp)
        settleFrames(TRANSITION_SETTLE_MS)
        val miniAgain = cover()
        assertEquals(1, miniAgain.sharedBoundsWidthsDp.size)
        assertEquals(mini.widthDp, miniAgain.widthDp, WIDTH_TOLERANCE_DP)
        assertTrue(
            "Opening must pass through an intermediate size; sampled " + opening.sharedBoundsWidthsDp,
            opening.widthDp > mini.widthDp && opening.widthDp < full.widthDp,
        )
        assertTrue(
            "Closing must pass through an intermediate size; sampled " + closing.sharedBoundsWidthsDp,
            closing.widthDp > mini.widthDp && closing.widthDp < full.widthDp,
        )
    }

    @Composable
    private fun ArtworkMotionFixture(
        expanded: MutableState<Boolean>,
        player: SharedPlayerUiState,
        bitmap: Bitmap,
    ) {
        val density = LocalDensity.current.density
        SideEffect { densityScale = density }
        EnoToneTheme {
            AppMotionRoot(
                inSettings = false,
                diagnostics = false,
                playerExpanded = expanded.value,
                music = { ArtworkMiniDock(expanded.value, player) },
                settings = {},
                player = { ArtworkFullPlayer(expanded, bitmap) },
            )
        }
    }

    @Composable
    private fun ArtworkMiniDock(playerExpanded: Boolean, player: SharedPlayerUiState) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppDockMotion(
                    playerExpanded = playerExpanded,
                    miniVisible = true,
                    dragProgress = 0f,
                    backProgress = 0f,
                    miniPlayer = {
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalEnoArtworkVisibility provides this,
                        ) {
                            EnoToneMiniPlayer(
                                player = player,
                                onOpen = {},
                                onPlayPause = {},
                                onNext = {},
                            )
                        }
                    },
                    bottomBar = {
                        EnoBottomBar(
                            items = listOf(EnoBottomBarItem(EnoIcons.HOME, "Home")),
                            selectedIndex = 0,
                            onSelect = {},
                        )
                    },
                )
            }
        }
    }

    @Composable
    private fun ArtworkFullPlayer(expanded: MutableState<Boolean>, bitmap: Bitmap) {
        NowPlayingScreen(
            trackId = "cover-id",
            title = "Artwork transition",
            artist = "Artist",
            album = "Album",
            artwork = bitmap,
            playbackState = "PLAYING",
            errorCode = 0,
            positionMs = 100L,
            durationMs = 1_000L,
            canSeek = true,
            controlsEnabled = true,
            hasPrevious = false,
            hasNext = false,
            queuePosition = 1,
            queueTotal = 1,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = false,
            onClose = { expanded.value = false },
            onSeekFraction = {},
            onPlayPause = {},
            onPrevious = {},
            onNext = {},
            onCycleRepeat = {},
            onToggleShuffle = {},
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
                return sample
            }
        }
        throw AssertionError(
            phase + " did not expose intermediate shared bounds between " +
                miniWidthDp + "dp and " + fullWidthDp + "dp; sampled frames=" + samples,
        )
    }

    private fun settleFrames(durationMs: Long) {
        rule.mainClock.advanceTimeBy(durationMs)
        rule.waitForIdle()
    }

    private fun cover(): Cover {
        val widthsDp = rule.onAllNodesWithTag(
            "eno-shared-artwork", useUnmergedTree = true,
        ).fetchSemanticsNodes().map { it.boundsInRoot.width / densityScale }
        return Cover(widthsDp)
    }

    private companion object {
        const val MINI_ARTWORK_DP = 44f
        const val FULL_ARTWORK_DP = 320f
        const val WIDTH_TOLERANCE_DP = 2f
        const val INITIAL_SETTLE_MS = 450L
        const val TRANSITION_SETTLE_MS = 450L
        const val MAX_TRANSITION_FRAMES = 36
    }
}
