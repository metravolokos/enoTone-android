package app.enotone.android

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.enotone.library.OfflineLibraryGroup

/**
 * Dedicated album details header, part of the album's scrollable track list.
 * Uses precisely the same bounded/cached artwork path as visible album tiles.
 * Never queries the database or attempts artwork for the mixed Unknown Album.
 */
@Composable
internal fun AlbumDetailHeader(
    group: OfflineLibraryGroup,
    year: Int?,
    genre: String?,
    artworkGroup: AlbumArtworkGroup?,
    repository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    canPlay: Boolean,
    onPlayAll: () -> Unit,
    canRefreshTags: Boolean,
    refreshingTags: Boolean,
    refreshMessage: String,
    onRefreshTags: () -> Unit,
) {
    // Lazy grid/list items are disposed during navigation. If we already
    // drew this source on another page, paint the RAM image immediately
    // instead of briefly resetting it to a placeholder.
    var cover by remember(group.key, artworkGroup, accountEmail) {
        mutableStateOf<Bitmap?>(artworkGroup?.let {
            repository.peekAlbum(accountEmail, it, requestedSide = 512)
        })
    }
    LaunchedEffect(group.key, artworkGroup, accountEmail, appPassword) {
        if (artworkGroup != null) {
            repository.cachedAlbumCover(accountEmail, artworkGroup, 512)?.let {
                cover = it
            }
            if (appPassword.isNotBlank()) {
                cover = repository.albumCover(accountEmail, appPassword,
                    artworkGroup, requestedSide = 512) ?: cover
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val image = cover
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = stringResource(R.string.album_cover_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(200.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_albums),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Text(
            group.name,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        group.subtitle?.takeIf { it.isNotBlank() }?.let { artist ->
            Text(
                artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (year != null) {
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!genre.isNullOrBlank()) {
            Text(
                text = genre,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.library_group_tracks, group.trackIds.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            enabled = canPlay,
            onClick = onPlayAll,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_play),
                    contentDescription = null,
                )
                Text(stringResource(R.string.album_play_all))
            }
        }
        OutlinedButton(
            enabled = canRefreshTags && !refreshingTags,
            onClick = onRefreshTags,
        ) {
            Text(stringResource(if (refreshingTags)
                R.string.album_refresh_busy else R.string.album_refresh_tags))
        }
        if (refreshMessage.isNotEmpty()) {
            Text(
                text = refreshMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
