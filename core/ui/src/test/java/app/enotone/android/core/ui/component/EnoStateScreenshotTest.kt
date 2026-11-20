package app.enotone.android.core.ui.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Roborazzi-образцы состояний и листа ошибки в обеих утверждённых темах Android v2. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w377dp-h816dp-xxxhdpi")
class EnoStateScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun welcomeGraphite() = captureState(
        EnoStateSampleKind.WELCOME, dark = true, name = "state_welcome_graphite",
    )

    @Test
    fun welcomePorcelain() = captureState(
        EnoStateSampleKind.WELCOME, dark = false, name = "state_welcome_porcelain",
    )

    @Test
    fun readingGraphite() = captureState(
        EnoStateSampleKind.READING, dark = true, name = "state_reading_graphite",
    )

    @Test
    fun readingPorcelain() = captureState(
        EnoStateSampleKind.READING, dark = false, name = "state_reading_porcelain",
    )

    @Test
    fun notFoundGraphite() = captureState(
        EnoStateSampleKind.NOT_FOUND, dark = true, name = "state_not_found_graphite",
    )

    @Test
    fun notFoundPorcelain() = captureState(
        EnoStateSampleKind.NOT_FOUND, dark = false, name = "state_not_found_porcelain",
    )

    @Test
    fun musicMissingGraphite() = captureState(
        EnoStateSampleKind.MUSIC_MISSING, dark = true, name = "state_music_missing_graphite",
    )

    @Test
    fun musicMissingPorcelain() = captureState(
        EnoStateSampleKind.MUSIC_MISSING, dark = false, name = "state_music_missing_porcelain",
    )

    @Test fun errorShortGraphite() = captureError(
        EnoErrorSheetSampleKind.SHORT, dark = true, "error_sheet_short_graphite",
    )

    @Test fun errorShortPorcelain() = captureError(
        EnoErrorSheetSampleKind.SHORT, dark = false, "error_sheet_short_porcelain",
    )

    @Test fun errorLongGraphite() = captureError(
        EnoErrorSheetSampleKind.LONG, dark = true, "error_sheet_long_graphite",
    )

    @Test fun errorLongPorcelain() = captureError(
        EnoErrorSheetSampleKind.LONG, dark = false, "error_sheet_long_porcelain",
    )

    @Test
    @Config(qualifiers = "w377dp-h816dp-xxxhdpi")
    fun errorLongFontScale2Graphite() = captureError(
        EnoErrorSheetSampleKind.LONG_FONT_SCALE_2,
        dark = true,
        "error_sheet_long_font_scale_2_graphite",
    )

    @Test
    @Config(qualifiers = "w377dp-h816dp-xxxhdpi")
    fun errorLongFontScale2Porcelain() = captureError(
        EnoErrorSheetSampleKind.LONG_FONT_SCALE_2,
        dark = false,
        "error_sheet_long_font_scale_2_porcelain",
    )

    private fun captureState(sample: EnoStateSampleKind, dark: Boolean, name: String) {
        rule.setContent { EnoStateSampleGallery(darkTheme = dark, sample = sample) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun captureError(sample: EnoErrorSheetSampleKind, dark: Boolean, name: String) {
        rule.setContent { EnoErrorSheetSampleGallery(darkTheme = dark, sample = sample) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
