package app.enotone.android

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    onShuffleAll: () -> Unit,
    canRefreshTags: Boolean,
    refreshingTags: Boolean,
    refreshMessage: String,
    onRefreshTags: () -> Unit,
    onArtworkLoaded: (Bitmap?) -> Unit = {},
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

    LaunchedEffect(cover) { onArtworkLoaded(cover) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(242.dp)
                .shadow(
                    elevation = 18.dp, shape = RoundedCornerShape(8.dp),
                    clip = false,
                    ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = .70f),
                    spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = .60f),
                )
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.album_cover_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(242.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_albums),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
        Text(
            group.name,
            style = MaterialTheme.typography.headlineMedium,
            color = androidx.compose.ui.graphics.Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        group.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineSmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = .83f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        val facts = listOfNotNull(
            genre?.takeIf { it.isNotBlank() },
            year?.toString(),
        ).joinToString(" · ")
        if (facts.isNotBlank()) {
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
            Text(
                text = facts,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = .68f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
        // Screenshot structure: small shuffle circle, big white play capsule,
        // small add circle (a disabled placeholder until playlists arrive).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp)
                    .background(
                        androidx.compose.ui.graphics.Color.White.copy(alpha = .13f),
                        RoundedCornerShape(26.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onShuffleAll, enabled = canPlay) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_shuffle),
                        contentDescription = stringResource(R.string.library_shuffle_all),
                        tint = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
            Button(
                onClick = onPlayAll,
                enabled = canPlay,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color.White,
                    contentColor = androidx.compose.ui.graphics.Color(0xFF202024),
                    disabledContainerColor = androidx.compose.ui.graphics.Color.White.copy(alpha=.62f),
                    disabledContentColor = androidx.compose.ui.graphics.Color.DarkGray,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_action_play), contentDescription = null,
                    modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.ui_play),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
            }
            Box(
                modifier = Modifier.size(52.dp).background(
                    androidx.compose.ui.graphics.Color.White.copy(alpha=.13f),
                    RoundedCornerShape(26.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(enabled = false, onClick = {}) {
                    Icon(painterResource(R.drawable.ic_music_add), contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        // Reindexing stays in overflow, with the same real callback.
        Box(modifier = Modifier.align(Alignment.End)) {
            IconButton(onClick = { actionsExpanded = true }) {
                Icon(painterResource(R.drawable.ic_action_more),
                    contentDescription = stringResource(R.string.album_actions),
                    tint = androidx.compose.ui.graphics.Color.White)
            }
            DropdownMenu(expanded = actionsExpanded,
                onDismissRequest = { actionsExpanded = false }) {
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
        if (refreshMessage.isNotBlank()) {
            Text(refreshMessage,
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha=.7f),
                textAlign = TextAlign.Center)
        }
    }
}
