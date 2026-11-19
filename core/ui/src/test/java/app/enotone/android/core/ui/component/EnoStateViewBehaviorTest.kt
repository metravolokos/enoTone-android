package app.enotone.android.core.ui.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import app.enotone.android.core.ui.theme.EnoToneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w377dp-h816dp-xxxhdpi")
class EnoStateViewBehaviorTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun titleIsAHeadingForTalkBack() {
        rule.setContent {
            EnoToneTheme(darkTheme = true) {
                EnoStateView(
                    pose = BanditPose.THINKING,
                    title = "Ничего не нашлось",
                    message = "Попробуйте ещё раз.",
                )
            }
        }

        val heading = rule.onNodeWithText("Ничего не нашлось").fetchSemanticsNode()
        assertTrue(heading.config.contains(SemanticsProperties.Heading))
    }

    @Test
    @Config(qualifiers = "w377dp-h450dp-xxxhdpi")
    fun largeTextCanScrollUntilTheActionIsVisible() {
        rule.setContent {
            EnoToneTheme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    EnoStateView(
                        pose = BanditPose.SITTING,
                        title = "Привет!",
                        message = LONG_STATE_MESSAGE,
                        primaryAction = EnoStateAction("Продолжить", {}),
                    )
                }
            }
        }

        rule.onNodeWithText("Продолжить").performScrollTo().assertIsDisplayed()
    }
}

private const val LONG_STATE_MESSAGE =
    "Я внимательно читаю вашу музыку и проверяю каждую папку. Если список большой, " +
        "можно продолжить позже: я сохраню найденные песни и вернусь к работе с того же места. " +
        "Пока идёт проверка, уже загруженную музыку можно слушать и открывать в медиатеке."
