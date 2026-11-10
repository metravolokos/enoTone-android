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
    repository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    allowNetwork: Boolean,
) {
    var cover by remember(trackId, target, accountEmail) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(trackId, target, accountEmail, appPassword, allowNetwork) {
        // Do not compete with a connecting/buffering audio stream. A later
        // READY state may request the cover; a previously loaded image remains.
        if (allowNetwork && target != null && appPassword.isNotBlank()) {
            cover = repository.cover(accountEmail, appPassword, target)
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
