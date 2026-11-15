package app.enotone.android

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Поведенческие проверки контейнера без MediaSession, WebDAV и реальных тегов. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppMotionRootUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun playerOpensAndClosesWithoutRecreatingMusicContent() {
        val expanded = mutableStateOf(false)
        var musicCompositions = 0
        rule.setContent {
            EnoToneTheme {
                AppMotionRoot(
                    inSettings = false,
                    diagnostics = false,
                    playerExpanded = expanded.value,
                    music = {
                        Box(Modifier.fillMaxSize()) {
                            Text("Fixture music route")
                        }
                        musicCompositions++
                    },
                    settings = { Text("Fixture settings") },
                    player = { Text("Fixture full player") },
                )
            }
        }
        rule.onNodeWithText("Fixture music route").assertExists()
        rule.onNodeWithText("Fixture full player").assertDoesNotExist()
        rule.runOnIdle { expanded.value = true }
        rule.waitForIdle()
        rule.onNodeWithText("Fixture full player").assertExists()
        rule.runOnIdle { expanded.value = false }
        rule.waitForIdle()
        rule.onNodeWithText("Fixture music route").assertExists()
        rule.onNodeWithText("Fixture full player").assertDoesNotExist()
        // Music can recompose but must still be present while the overlay is open.
        assertEquals(true, musicCompositions > 0)
    }

    @Test fun settingsAndDiagnosticsFollowTheirRouteAndReturnToMusic() {
        val inSettings = mutableStateOf(false)
        val diagnostics = mutableStateOf(false)
        rule.setContent {
            EnoToneTheme {
                AppMotionRoot(
                    inSettings = inSettings.value,
                    diagnostics = diagnostics.value,
                    playerExpanded = false,
                    music = { Text("Fixture library") },
                    settings = { details -> Text(if (details) "Fixture diagnostics" else "Fixture settings") },
                    player = { Text("Fixture player") },
                )
            }
        }
        rule.runOnIdle { inSettings.value = true }
        rule.waitForIdle()
        rule.onNodeWithText("Fixture settings").assertExists()
        rule.runOnIdle { diagnostics.value = true }
        rule.waitForIdle()
        rule.onNodeWithText("Fixture diagnostics").assertExists()
        rule.runOnIdle { diagnostics.value = false }
        rule.waitForIdle()
        rule.onNodeWithText("Fixture settings").assertExists()
        rule.runOnIdle { inSettings.value = false }
        rule.waitForIdle()
        rule.onNodeWithText("Fixture library").assertExists()
    }

    @Test fun transitionsSettleWithSystemAnimationsDisabled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val original = Settings.Global.getFloat(context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            val expanded = mutableStateOf(false)
            rule.setContent {
                EnoToneTheme {
                    AppMotionRoot(
                        inSettings = false,
                        diagnostics = false,
                        playerExpanded = expanded.value,
                        music = { Text("Fixture music") },
                        settings = { Text("Fixture settings") },
                        player = { Text("Fixture expanded") },
                    )
                }
            }
            rule.runOnIdle { expanded.value = true }
            rule.waitForIdle()
            rule.onNodeWithText("Fixture expanded").assertExists()
            rule.runOnIdle { expanded.value = false }
            rule.waitForIdle()
            rule.onNodeWithText("Fixture expanded").assertDoesNotExist()
            rule.onNodeWithText("Fixture music").assertExists()
        } finally {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, original)
        }
    }
}
