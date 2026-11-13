package app.enotone.android.core.ui.component

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.R
import app.enotone.android.core.ui.theme.EnoColors

@Composable
fun BanditIllustration(
    pose: BanditPose,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(pose.drawableId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

/** Wordmark — только готовый XML drawable v2, не набирается текстовым шрифтом. */
@Composable
fun EnoWordmark(
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    // Unlike a flat-tinted icon, the wordmark is two-colour and uses night-qualified
    // XML vector paints. MaterialTheme can override system UI mode without changing
    // Android resources, so scope the resource context to the painter only.
    val dark = EnoColors.text.luminance() > LIGHT_TEXT_LUMINANCE
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val configuration = remember(baseContext, baseConfiguration, dark) {
        Configuration(baseConfiguration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
    }
    val themedContext = remember(baseContext, configuration) {
        baseContext.createConfigurationContext(configuration)
    }
    CompositionLocalProvider(
        LocalContext provides themedContext,
        LocalConfiguration provides configuration,
    ) {
        Image(
            painter = painterResource(R.drawable.enotone_wordmark),
            contentDescription = contentDescription,
            modifier = modifier.defaultMinSize(minWidth = 120.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

/** Только двенадцать утверждённых поз, доступных в D1; без wink-pointing и headphones-A. */
enum class BanditPose(@DrawableRes val drawableId: Int) {
    BOOKS(R.drawable.bandit_books),
    READING(R.drawable.bandit_reading),
    THINKING(R.drawable.bandit_thinking),
    PEEKING(R.drawable.bandit_peeking),
    HAPPY(R.drawable.bandit_happy),
    RELAXING(R.drawable.bandit_relaxing),
    SLEEPING(R.drawable.bandit_sleeping),
    SLEEPING_BOOKS(R.drawable.bandit_sleeping_books),
    ARMCHAIR(R.drawable.bandit_armchair),
    SITTING(R.drawable.bandit_sitting),
    IDEA(R.drawable.bandit_idea),
    HEADPHONES(R.drawable.bandit_headphones),
}

/** Светлый основной текст = тёмная тема («Графит»). */
private const val LIGHT_TEXT_LUMINANCE = 0.5f
