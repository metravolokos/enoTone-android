package app.enotone.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Same drag threshold as the approved library mini-player; px from pointer input.
private const val MINI_OPEN_SWIPE_PX = 55f

/** One pixel-identical Material 3 mini-player reused on Home, Library and Settings. */
@Composable
internal fun EnoToneMiniPlayer(
    player: SharedPlayerUiState,
    selecting: Boolean = false,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val playback = player.playback
    val selectedTitle = playback.title ?: stringResource(R.string.ui_songs_title)
    val selectedArtist = playback.artist
    val currentArtwork = player.artwork
    val playbackState = playback.playbackState
    val errorCode = playback.errorCode
    // Native Material 3 mini-player: a raised Surface above the NavigationBar.
    if (playback.queueTotal > 0) {
        Surface(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
        ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp)
                .pointerInput(Unit) {
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onVerticalDrag = { change, distance ->
                            dragged += distance
                            change.consume()
                        },
                        onDragEnd = { if (dragged < -MINI_OPEN_SWIPE_PX) onOpen() },
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover and metadata both open Now Playing. Two transport
            // actions stay visible; a redundant third "expand" button
            // would take space away from long track titles.
            Box(
                modifier = Modifier.size(48.dp).clickable(
                    onClickLabel = stringResource(R.string.library_player_open),
                ) {
                    onOpen()
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(6.dp),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (currentArtwork != null) {
                        Image(
                            bitmap = currentArtwork.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                                .clip(RoundedCornerShape(6.dp)),
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
            val compactStatus = when (playbackState) {
                "BUFFERING" -> stringResource(R.string.playback_buffering)
                "PLAYING" -> stringResource(R.string.playback_playing)
                "PAUSED" -> stringResource(R.string.playback_paused)
                "ERROR" -> stringResource(R.string.playback_error, errorCode)
                "MISSING_PASSWORD" -> stringResource(R.string.playback_missing_password)
                "MISSING_TRACK" -> stringResource(R.string.playback_missing_track)
                "ENDED" -> stringResource(R.string.playback_ended)
                else -> stringResource(R.string.playback_loading)
            }
            Column(
                modifier = Modifier.weight(1f).clickable {
                    onOpen()
                }.padding(start = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(selectedTitle, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
                val subtitle = if (playbackState in listOf(
                    "ERROR", "MISSING_PASSWORD", "MISSING_TRACK"))
                    compactStatus else selectedArtist.orEmpty()
                Text(subtitle, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val paused = playbackState != "PLAYING" && playbackState != "BUFFERING"
            IconButton(
                enabled = playback.connected && !selecting,
                onClick = { onPlayPause() },
            ) {
                Icon(
                    painter = painterResource(if (paused)
                        R.drawable.ic_action_play else R.drawable.ic_action_pause),
                    modifier = Modifier.size(28.dp),
                    contentDescription = stringResource(if (paused)
                        R.string.playback_resume else R.string.playback_pause),
                )
            }
            IconButton(
                enabled = playback.hasNext && !selecting,
                onClick = { onNext() },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_next),
                    contentDescription = stringResource(R.string.playback_next),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        }
    }

}
