package app.enotone.android.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.R
import app.enotone.android.core.ui.theme.EnoColors

@Composable
fun EnoIcon(
    icon: EnoIcons,
    description: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 24.dp,
) {
    val paint: Painter = painterResource(if (selected) icon.selected else icon.outline)
    val tint = if (selected) EnoColors.accentText else MaterialTheme.colorScheme.onSurface
    val visualModifier = modifier.size(size).then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA))
    Image(
        painter = paint,
        contentDescription = description,
        modifier = visualModifier,
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Значок UI Icons v1; выбранная вкладка использует отдельный fill-вектор. */
enum class EnoIcons(@DrawableRes val outline: Int, @DrawableRes val selected: Int = outline) {
    HOME(R.drawable.ic_eno_home, R.drawable.ic_eno_home_fill),
    SONGS(R.drawable.ic_eno_songs, R.drawable.ic_eno_songs_fill),
    ALBUMS(R.drawable.ic_eno_albums, R.drawable.ic_eno_albums_fill),
    ARTISTS(R.drawable.ic_eno_artists, R.drawable.ic_eno_artists_fill),
    ALBUM_ARTISTS(R.drawable.ic_eno_album_artists, R.drawable.ic_eno_album_artists_fill),
    MORE(R.drawable.ic_eno_more),
    PLAY(R.drawable.ic_eno_play),
    PAUSE(R.drawable.ic_eno_pause),
    PREVIOUS(R.drawable.ic_eno_previous),
    NEXT(R.drawable.ic_eno_next),
    SHUFFLE(R.drawable.ic_eno_shuffle),
    REPEAT(R.drawable.ic_eno_repeat),
    REPEAT_ONE(R.drawable.ic_eno_repeat_one),
    QUEUE(R.drawable.ic_eno_queue),
}

/** Прозрачность отключённого элемента по Material 3. */
private const val DISABLED_ALPHA = 0.38f
