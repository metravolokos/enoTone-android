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
 * A decorative thumbnail for one composed (visible) row in a LazyColumn.
 * Never starts an index, queries Room, or preloads off-screen tracks. The
 * shared repository bounds WebDAV image GETs and reuses its private cache.
 */
@Composable
internal fun TrackArtworkThumbnail(
    trackId: String,
    target: PlayingArtworkTarget?,
    albumPreviewTarget: PlayingArtworkTarget?,
    repository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    allowNetwork: Boolean,
) {
    // Reuse the exact source if cached. An already visible album cover may
    // serve ONLY as a temporary visual preview: never alias its hash to this
    // track's ref, because two tracks in one album can have different art.
    var cover by remember(trackId, target, albumPreviewTarget, accountEmail) {
        mutableStateOf<Bitmap?>(
            target?.let { repository.peek(accountEmail, it, requestedSide = 192) }
                ?: albumPreviewTarget?.let {
                    repository.peek(accountEmail, it, requestedSide = 192)
                },
        )
    }

    LaunchedEffect(trackId, target, albumPreviewTarget, accountEmail,
        appPassword, allowNetwork) {
        // Network checks only for visible tracks, never during import. While
        // waiting, the already cached album preview remains visible.
        if (allowNetwork && target != null && appPassword.isNotBlank()) {
            if (cover == null && albumPreviewTarget != null &&
                albumPreviewTarget.id != target.id) {
                // If RAM was evicted on a page switch, first read the verified
                // album master locally; DO NOT request its source via HTTP.
                repository.cachedCover(accountEmail, albumPreviewTarget, 192)?.let {
                    cover = it
                }
            }
            cover = repository.cover(accountEmail, appPassword, target, requestedSide = 192)
            // A confirmed individual cover overrides the album preview.
            // A genuinely absent one becomes the regular placeholder.
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
                contentDescription = null, // Title and artist label the parent row.
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
