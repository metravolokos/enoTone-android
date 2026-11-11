package app.enotone.android

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp

/**
 * Album art is shared by EVERY member of the same actual album.
 * Virtual "Unknown Album" tracks receive independent single-track groups.
 * No Room lookup, index or network request occurs outside visible rows.
 */
@Composable
internal fun TrackArtworkThumbnail(
    trackId: String,
    artworkGroup: AlbumArtworkGroup?,
    repository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    allowNetwork: Boolean,
) {
    var cover by remember(trackId, artworkGroup, accountEmail) {
        mutableStateOf<Bitmap?>(artworkGroup?.let {
            repository.peekAlbum(accountEmail, it, requestedSide = 192)
        })
    }

    LaunchedEffect(trackId, artworkGroup, accountEmail, appPassword, allowNetwork) {
        if (artworkGroup != null) {
            // Disk-only check is allowed while audio is buffering.
            repository.cachedAlbumCover(accountEmail, artworkGroup, 192)?.let {
                cover = it
            }
            // Only user-visible entries can perform a new bounded WebDAV GET.
            if (allowNetwork && appPassword.isNotBlank()) {
                cover = repository.albumCover(accountEmail, appPassword,
                    artworkGroup, requestedSide = 192) ?: cover
            }
        }
    }

    Box(
        modifier = Modifier.size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = cover
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_nav_tracks),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
