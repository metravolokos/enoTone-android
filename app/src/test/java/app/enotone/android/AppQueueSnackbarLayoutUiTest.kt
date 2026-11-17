package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

/** Verifies that the collapsed music Snackbar stays above the mini-player and navigation dock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class AppQueueSnackbarLayoutUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun collapsedSnackbarIsEightDpAboveDockAndUsesTwelveDpSideInsets() {
        val density = AtomicReference<Float>()
        rule.setContent {
            val snackbar = remember { SnackbarHostState() }
            val pxPerDp = LocalDensity.current.density
            SideEffect { density.set(pxPerDp) }
            LaunchedEffect(snackbar) {
                snackbar.showSnackbar("Queue message", duration = SnackbarDuration.Indefinite)
            }
            EnoToneTheme {
                AppMotionRoot(
                    inSettings = false,
                    diagnostics = false,
                    playerExpanded = false,
                    queueSnackbar = snackbar,
                    queueSnackbarBottomPadding = snackbarBottomOffset(miniVisible = true),
                    queueSnackbarHorizontalPadding = DOCK_SIDE,
                    music = {
                        Box(Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier.align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(
                                        start = DOCK_SIDE,
                                        end = DOCK_SIDE,
                                        bottom = DOCK_SIDE,
                                    )
                                    .testTag("test-collapsed-dock"),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Spacer(Modifier.fillMaxWidth().height(MINI_HEIGHT))
                                Spacer(Modifier.fillMaxWidth().height(70.dp))
                            }
                        }
                    },
                    settings = {},
                    player = {},
                )
            }
        }

        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Queue message").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Queue message").assertIsDisplayed()
        val host = rule.onNodeWithTag("app-queue-snackbar-host", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val dock = rule.onNodeWithTag("test-collapsed-dock", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pxPerDp = requireNotNull(density.get())
        val expectedGap = 8.dp.value * pxPerDp
        val expectedSideInset = DOCK_SIDE.value * pxPerDp
        assertTrue("Snackbar should sit one dock gap above the dock: $host / $dock",
            kotlin.math.abs((dock.top - host.bottom) - expectedGap) <= 2f)
        assertTrue("Snackbar should keep the dock's left inset: $host / $root",
            host.left >= expectedSideInset - 2f)
        assertTrue("Snackbar should keep the dock's right inset: $host / $root",
            host.right <= root.right - expectedSideInset + 2f)
    }
}
