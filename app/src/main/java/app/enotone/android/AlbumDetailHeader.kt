package app.enotone.android

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.android.core.ui.component.EnoPill
import app.enotone.android.core.ui.component.EnoRoundButton
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
    onShuffleAll: () -> Unit,
    canRefreshTags: Boolean,
    refreshingTags: Boolean,
    refreshMessage: String,
    onRefreshTags: () -> Unit,
) {
    var actionsExpanded by remember(group.key) { mutableStateOf(false) }
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
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(232.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.album_cover_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(232.dp),
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_nav_albums),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Text(group.name, style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        group.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center)
        }
        val facts = listOfNotNull(genre?.takeIf { it.isNotBlank() }, year?.toString())
            .joinToString(" · ")
        if (facts.isNotBlank()) {
            Text(facts, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium)
        }
        // D3.1: «Играть» первая (акцент), «Перемешать» (surface2), «⋮» — круг surface2 Ø 48 (05_album).
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EnoPill(
                label = stringResource(R.string.ui_play), accent = true, icon = EnoIcons.PLAY,
                enabled = canPlay, onClick = onPlayAll, horizontalPadding = ALBUM_PILL_PADDING,
                modifier = Modifier.weight(1f),
            )
            EnoPill(
                label = stringResource(R.string.library_shuffle_all), icon = EnoIcons.SHUFFLE,
                enabled = canPlay, onClick = onShuffleAll, horizontalPadding = ALBUM_PILL_PADDING,
                modifier = Modifier.weight(1f),
            )
            Box {
                EnoRoundButton(
                    icon = EnoIcons.MORE,
                    contentDescription = stringResource(R.string.album_actions),
                    size = 48.dp,
                    onClick = { actionsExpanded = true },
                )
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (refreshingTags)
                            R.string.album_refresh_busy else R.string.album_refresh_tags)) },
                        enabled = canRefreshTags && !refreshingTags,
                        onClick = {
                            actionsExpanded = false
                            onRefreshTags()
                        },
                    )
                }
            }
        }
        if (refreshMessage.isNotBlank()) {
            Text(refreshMessage, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

/** Поля пилюль в шапке альбома: две пилюли и «⋮» в 321 dp без обрезки подписей. */
private val ALBUM_PILL_PADDING = 4.dp
