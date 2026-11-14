package app.enotone.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.android.core.ui.theme.EnoElevation
import app.enotone.android.core.ui.theme.EnoRadii
import androidx.compose.ui.unit.dp
import app.enotone.library.OfflineLibraryGroup
import android.graphics.Bitmap

/**
 * Real album tiles. Only items composed by LazyVerticalGrid request artwork;
 * leaving the viewport cancels their LaunchedEffect. Image loading shares the
 * repository's two-slot semaphore with the current-track cover.
 */
@Composable
internal fun AlbumArtworkTile(
    group: OfflineLibraryGroup,
    artworkGroup: AlbumArtworkGroup?,
    repository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    onOpen: () -> Unit,
    subtitle: String? = group.subtitle,
    artistCarousel: Boolean = false,
) {
    // Lazy grid/list items are disposed during navigation. If we already
    // drew this source on another page, paint the RAM image immediately
    // instead of briefly resetting it to a placeholder.
    var cover by remember(group.key, artworkGroup, accountEmail) {
        mutableStateOf<Bitmap?>(artworkGroup?.let {
            repository.peekAlbum(accountEmail, it, requestedSide = 384)
        })
    }
    LaunchedEffect(group.key, artworkGroup, accountEmail, appPassword) {
        if (artworkGroup != null) {
            repository.cachedAlbumCover(accountEmail, artworkGroup, 384)?.let {
                cover = it
            }
            if (appPassword.isNotBlank()) {
                cover = repository.albumCover(accountEmail, appPassword,
                    artworkGroup, requestedSide = 384) ?: cover
            }
        }
    }

    val coverShape = RoundedCornerShape(if (artistCarousel) EnoRadii.s else 8.dp)
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                .shadow(2.dp, coverShape)
                .clip(coverShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val image = cover
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = null, // Group title announces the card.
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_albums),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (artistCarousel) {
                Box(Modifier.matchParentSize().border(
                    EnoElevation.coverBorder, EnoColors.coverOutline, coverShape,
                ))
            }
        }
        Text(
            text = group.name,
            style = if (artistCarousel) MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp, fontWeight = FontWeight.Medium,
            ) else MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val secondary = subtitle.orEmpty()
        if (secondary.isNotEmpty()) {
            Text(
                text = secondary,
                style = if (artistCarousel) MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                    else MaterialTheme.typography.bodySmall,
                color = if (artistCarousel) EnoColors.textSecondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
