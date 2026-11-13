package app.enotone.android.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.R

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
    Image(
        painter = painterResource(R.drawable.enotone_wordmark),
        contentDescription = contentDescription,
        modifier = modifier.defaultMinSize(minWidth = 120.dp),
        contentScale = ContentScale.Fit,
    )
}
