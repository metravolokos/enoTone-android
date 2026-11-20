package app.enotone.android

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.enotone.android.core.ui.theme.EnoToneTheme as EnoTheme

/** D6-B Settings header and the separate Library card in both Android v2 themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w377dp-h816dp-xxxhdpi")
class SettingsHomeScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun graphite() = captureSettings(dark = true)

    @Test fun porcelain() = captureSettings(dark = false)

    private fun captureSettings(dark: Boolean) {
        rule.setContent {
            Screen(dark) {
                SettingsHomeContent(
                    version = "0.6.2",
                    cloudContent = {
                        AccountSettingsSection(
                            account = AccountUiState(
                                email = "demo@example.invalid",
                                restoring = false,
                                loggedIn = true,
                                trackCount = 3590,
                            ),
                            onEmailChange = {},
                            onPasswordChange = {},
                            onVerify = {},
                            onRequestSignOut = {},
                            onConfirmSignOut = {},
                            onCancelSignOut = {},
                        )
                    },
                    mediaContent = {
                        SettingsIndexingSection(
                            LibraryIndexingUiState(hasLogin = true),
                            object : IndexingActions {},
                        )
                    },
                    appearanceContent = {
                        AppearanceSettingsContent(UiMode.SYSTEM, {})
                    },
                    libraryPagesContent = {
                        LibraryVisibilitySettingsContent(emptySet()) {}
                    },
                    artworkContent = {
                        SettingsArtworkSection(
                            state = ArtworkSettingsUiState(storedBytes = 512L * 1024L * 1024L),
                            onModeChange = {},
                            onClear = {},
                            onPause = {},
                            onStop = {},
                        )
                    },
                    onOpenDiagnostics = {},
                )
            }
        }
        rule.waitForIdle()
        capture("settings_" + theme(dark))
        rule.onRoot().performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.artwork_settings_title)).assertIsDisplayed()
        capture("settings_library_" + theme(dark))
    }

    @Composable
    private fun Screen(dark: Boolean, content: @Composable () -> Unit) {
        EnoTheme(dark) {
            Surface(color = MaterialTheme.colorScheme.background, content = content)
        }
    }

    private fun capture(name: String) =
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    private fun theme(dark: Boolean) = if (dark) "graphite" else "porcelain"
}
