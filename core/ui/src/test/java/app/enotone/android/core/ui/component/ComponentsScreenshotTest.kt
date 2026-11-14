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

/** Все компоненты на одном снимке для каждой темы, с выдуманными названиями. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class ComponentsScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun graphiteComponents() {
        composeRule.setContent { ComponentGallery(darkTheme = true) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/components_graphite.png")
    }

    @Test fun porcelainComponents() {
        composeRule.setContent { ComponentGallery(darkTheme = false) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/components_porcelain.png")
    }

    @Test fun graphiteShell() {
        composeRule.setContent { ShellGallery(darkTheme = true) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/shell_graphite.png")
    }

    @Test fun porcelainShell() {
        composeRule.setContent { ShellGallery(darkTheme = false) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/shell_porcelain.png")
    }
}
