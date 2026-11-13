package app.enotone.android.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.android.core.ui.theme.EnoElevation
import app.enotone.android.core.ui.theme.EnoRadii

/** Все загружаемые обложки — слот: собственный слой кэша/изображений в :app/:feature. */
@Composable
fun EnoTrackRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onMore: () -> Unit,
    moreContentDescription: String,
    playingDescription: String,
    modifier: Modifier = Modifier,
    artwork: (@Composable () -> Unit)? = null,
    variant: EnoTrackRowVariant = EnoTrackRowVariant.LIBRARY,
    trackNumber: String = "",
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    animatedIndicator: Boolean = true,
) {
    val album = variant == EnoTrackRowVariant.ALBUM
    Row(
        modifier = modifier.fillMaxWidth()
            .height(if (album) 52.dp else 64.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (album) {
            Box(Modifier.width(26.dp).height(40.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) {
                    EnoPlayingIndicator(
                        playing = isPlaying,
                        contentDescription = playingDescription,
                        animated = animatedIndicator,
                        variant = PlayingIndicatorVariant.TRACK_NUMBER,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(trackNumber, color = EnoColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Box(
                modifier = Modifier.size(44.dp)
                    .clip(RoundedCornerShape(EnoRadii.xs))
                    .background(EnoColors.surface2),
            ) {
                artwork?.invoke()
                // Окантовка поверх обложки: светлые обложки не сливаются с фоном.
                Box(
                    Modifier.matchParentSize()
                        .border(EnoElevation.coverBorder, EnoColors.coverOutline, RoundedCornerShape(EnoRadii.xs)),
                )
                if (isCurrent) {
                    EnoPlayingIndicator(
                        playing = isPlaying,
                        contentDescription = playingDescription,
                        animated = animatedIndicator,
                        variant = PlayingIndicatorVariant.ARTWORK,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) EnoColors.accentText else EnoColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = EnoColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EnoRoundButton(
            icon = EnoIcons.MORE,
            contentDescription = moreContentDescription,
            onClick = onMore,
            size = 40.dp,
        )
    }
}

enum class EnoTrackRowVariant { LIBRARY, ALBUM }
