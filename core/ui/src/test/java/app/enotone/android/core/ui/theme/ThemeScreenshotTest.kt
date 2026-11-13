package app.enotone.android.core.ui.theme

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Изображения записываются в CI, а затем verify сравнивает их с закоммиченными эталонами. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class ThemeScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun graphitePaletteTypographyAndRadii() {
        composeRule.setContent { ThemeSpecimen(darkTheme = true) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/theme_graphite.png")
    }

    @Test
    fun porcelainPaletteTypographyAndRadii() {
        composeRule.setContent { ThemeSpecimen(darkTheme = false) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/theme_porcelain.png")
    }
}
