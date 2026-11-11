package app.enotone.android

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.enotone.playback.PlaybackSeekPolicy

/**
 * User-facing Now Playing, not a dialog and not a second Media3 player.
 * The surrounding OfflineLibraryScreen owns the existing bound service and
 * supplies its live state. This view never queries Room or WebDAV directly.
 */
@Composable
internal fun NowPlayingScreen(
    trackId: String,
    title: String,
    artist: String?,
    album: String?,
    artwork: Bitmap?,
    playbackState: String,
    errorCode: Int,
    positionMs: Long,
    durationMs: Long,
    canSeek: Boolean,
    controlsEnabled: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    queuePosition: Int,
    queueTotal: Int,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    onClose: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
) {
    var isScrubbing by remember(trackId) { mutableStateOf(false) }
    var scrubFraction by remember(trackId) { mutableStateOf(0f) }
    val hasDuration = PlaybackSeekPolicy.isKnownDuration(durationMs)
    val shownFraction = if (isScrubbing) scrubFraction
        else PlaybackSeekPolicy.fraction(positionMs, durationMs)
    val shownPosition = if (isScrubbing) {
        PlaybackSeekPolicy.positionForFraction(durationMs, scrubFraction) ?: 0L
    } else positionMs
    val pauseIsAvailable = playbackState == "PLAYING" || playbackState == "BUFFERING"
    val repeatText = when (repeatMode) {
        Player.REPEAT_MODE_ONE -> stringResource(R.string.playback_repeat_one)
        Player.REPEAT_MODE_ALL -> stringResource(R.string.playback_repeat_all)
        else -> stringResource(R.string.playback_repeat_off)
    }
    val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
    val stateText = when (playbackState) {
        "BUFFERING" -> stringResource(R.string.playback_buffering)
        "PAUSED" -> stringResource(R.string.playback_paused)
        "ENDED" -> stringResource(R.string.playback_ended)
        "ERROR" -> stringResource(R.string.playback_error, errorCode)
        "MISSING_PASSWORD" -> stringResource(R.string.playback_missing_password)
        "MISSING_TRACK" -> stringResource(R.string.playback_missing_track)
        "LOADING" -> stringResource(R.string.playback_loading)
        else -> ""
    }
    val seekLabel = stringResource(R.string.playback_seek_slider)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sampled = remember(artwork) { artworkColors(artwork) }
    // Dark mode falls toward black; light mode remains pale to keep ink legible.
    val desiredTop = if (dark) lerp(sampled.first, Color.Black, 0.59f)
        else lerp(sampled.first, Color.White, 0.76f)
    val desiredBottom = if (dark) lerp(sampled.second, Color.Black, 0.88f)
        else lerp(sampled.second, Color.White, 0.90f)
    val topColor by animateColorAsState(desiredTop, tween(550), label = "artworkTop")
    val bottomColor by animateColorAsState(desiredBottom, tween(550), label = "artworkBottom")
    val coverScale by animateFloatAsState(
        targetValue = if (playbackState == "PAUSED") 0.88f else 1f,
        animationSpec = tween(260),
        label = "pausedArtworkScale",
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(topColor, bottomColor)),
        ),
    ) {
        // Scrolling keeps every playback control reachable on small/landscape
        // screens, while a large square cover anchors the portrait layout.
        val coverSide = minOf(
            (maxWidth - 56.dp).coerceAtLeast(120.dp),
            (maxHeight * 0.42f).coerceAtLeast(168.dp),
            420.dp,
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_back),
                        contentDescription = stringResource(R.string.library_player_close),
                    )
                }
                Text(
                    text = stringResource(R.string.now_playing_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.size(coverSide)
                    .graphicsLayer { scaleX = coverScale; scaleY = coverScale }
                    .shadow(20.dp, RoundedCornerShape(18.dp), clip = false)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork.asImageBitmap(),
                        contentDescription = stringResource(R.string.artwork_description),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_albums),
                        contentDescription = stringResource(R.string.now_playing_no_artwork),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!artist.isNullOrBlank()) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!album.isNullOrBlank()) {
                Text(
                    text = album,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (stateText.isNotBlank()) {
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = shownFraction,
                onValueChange = { value ->
                    isScrubbing = true
                    scrubFraction = value
                },
                onValueChangeFinished = {
                    if (canSeek && controlsEnabled && hasDuration) {
                        onSeekFraction(scrubFraction)
                    }
                    isScrubbing = false
                },
                enabled = canSeek && controlsEnabled && hasDuration,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = seekLabel
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = PlaybackSeekPolicy.formatTime(shownPosition),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (hasDuration) PlaybackSeekPolicy.formatTime(durationMs)
                        else stringResource(R.string.playback_duration_unknown),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = controlsEnabled && hasPrevious,
                    onClick = onPrevious,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_previous),
                        contentDescription = stringResource(R.string.playback_previous),
                        modifier = Modifier.size(30.dp),
                    )
                }
                FilledIconButton(
                    enabled = controlsEnabled,
                    onClick = onPlayPause,
                    modifier = Modifier.size(68.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(
                        painter = painterResource(
                            if (pauseIsAvailable) R.drawable.ic_action_pause
                            else R.drawable.ic_action_play,
                        ),
                        contentDescription = stringResource(
                            if (pauseIsAvailable) R.string.playback_pause
                            else R.string.playback_resume,
                        ),
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(
                    enabled = controlsEnabled && hasNext,
                    onClick = onNext,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_next),
                        contentDescription = stringResource(R.string.playback_next),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = controlsEnabled && queueTotal > 0,
                    onClick = onToggleShuffle,
                    modifier = Modifier.background(
                        if (shuffleEnabled) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent, RoundedCornerShape(12.dp)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_shuffle),
                        contentDescription = stringResource(
                            if (shuffleEnabled) R.string.playback_shuffle_on
                            else R.string.playback_shuffle_off),
                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (queueTotal > 0) {
                    Text(
                        text = stringResource(R.string.now_playing_queue_short,
                            queuePosition, queueTotal),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    enabled = controlsEnabled && queueTotal > 0,
                    onClick = onCycleRepeat,
                    modifier = Modifier.background(
                        if (repeatActive) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent, RoundedCornerShape(12.dp)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_repeat),
                        contentDescription = stringResource(
                            R.string.playback_repeat_control, repeatText),
                        tint = if (repeatActive) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
