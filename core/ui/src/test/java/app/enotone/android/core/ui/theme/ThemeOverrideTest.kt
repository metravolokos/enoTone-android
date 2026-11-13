package app.enotone.android.core.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.enotone.android.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Проверяет реальный android night resource qualifier при ручном переключении темы. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeOverrideTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "notnight")
    fun darkChoiceOverridesLightSystemResources() {
        verifyBackground(darkTheme = true, nightQualifier = Configuration.UI_MODE_NIGHT_YES)
    }

    @Test
    @Config(qualifiers = "night")
    fun lightChoiceOverridesDarkSystemResources() {
        verifyBackground(darkTheme = false, nightQualifier = Configuration.UI_MODE_NIGHT_NO)
    }

    private fun verifyBackground(darkTheme: Boolean, nightQualifier: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightQualifier
        }
        val expected = Color(context.createConfigurationContext(configuration).getColor(R.color.eno_background))
        var background: Color? = null
        var parentContext: Context? = null
        var contentContext: Context? = null
        composeRule.setContent {
            val originalContext = LocalContext.current
            EnoToneTheme(darkTheme) {
                val actualContext = LocalContext.current
                val schemeBackground = MaterialTheme.colorScheme.background
                SideEffect {
                    background = schemeBackground
                    parentContext = originalContext
                    contentContext = actualContext
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(expected, background)
        assertSame("Theme must not replace Activity context for content", parentContext, contentContext)
    }
}
