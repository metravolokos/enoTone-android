package app.enotone.android

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.enotone.playback.PlaybackSeekPolicy

/**
 * Material 3 Expressive / Graphite player. No artwork-sourced colors, fake
 * quality indicators, favorites, lyrics, cast, queue sheet or disabled icons.
 * All visible actions call the existing Media3 service or Android audio API.
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
    val color = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val audio = remember(ctx) {
        ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember(audio) {
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volume by remember(audio) {
        mutableFloatStateOf(
            audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume,
        )
    }

    val knownDuration = PlaybackSeekPolicy.isKnownDuration(durationMs)
    var scrubbing by remember(trackId) { mutableStateOf(false) }
    var scrubFraction by remember(trackId) { mutableFloatStateOf(0f) }
    val shownFraction = if (scrubbing) scrubFraction
        else PlaybackSeekPolicy.fraction(positionMs, durationMs)
    val shownPosition = if (scrubbing)
        PlaybackSeekPolicy.positionForFraction(durationMs, scrubFraction) ?: 0L
        else positionMs
    val isPlaying = playbackState == "PLAYING" || playbackState == "BUFFERING"
    val realError = when (playbackState) {
        "ERROR" -> stringResource(R.string.playback_error, errorCode)
        "MISSING_PASSWORD" -> stringResource(R.string.playback_missing_password)
        "MISSING_TRACK" -> stringResource(R.string.playback_missing_track)
        else -> null
    }
    val repeatText = when (repeatMode) {
        Player.REPEAT_MODE_ONE -> stringResource(R.string.playback_repeat_one)
        Player.REPEAT_MODE_ALL -> stringResource(R.string.playback_repeat_all)
        else -> stringResource(R.string.playback_repeat_off)
    }
    val coverScale by animateFloatAsState(
        targetValue = if (playbackState == "PAUSED") 0.84f else 1f,
        animationSpec = tween(260), label = "pausedGraphiteCover",
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(color.surfaceContainerHigh, color.background),
            ),
        ),
    ) {
        val coverSide = minOf(maxWidth - 56.dp, maxHeight * 0.38f, 320.dp)
        Column(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_action_back),
                        contentDescription = stringResource(R.string.library_player_close))
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.now_playing_title),
                        style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.weight(.25f))
            Box(
                modifier = Modifier.size(coverSide + 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(coverSide)
                        .graphicsLayer { scaleX = coverScale; scaleY = coverScale }
                        .shadow(20.dp, RoundedCornerShape(20.dp), clip = false)
                        .clip(RoundedCornerShape(20.dp))
                        .background(color.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (artwork != null) {
                        Image(
                            bitmap = artwork.asImageBitmap(),
                            contentDescription = stringResource(R.string.artwork_description),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(painterResource(R.drawable.ic_nav_albums),
                            contentDescription = stringResource(R.string.now_playing_no_artwork),
                            modifier = Modifier.size(64.dp),
                            tint = color.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.weight(.32f))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    realError ?: artist?.takeIf { it.isNotBlank() }
                        ?: album.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Slider(
                value = shownFraction.coerceIn(0f, 1f),
                enabled = controlsEnabled && canSeek && knownDuration,
                onValueChange = { next ->
                    scrubFraction = next
                    scrubbing = true
                },
                onValueChangeFinished = {
                    if (controlsEnabled && canSeek && knownDuration) {
                        onSeekFraction(scrubFraction)
                    }
                    scrubbing = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(PlaybackSeekPolicy.formatTime(shownPosition),
                    style = MaterialTheme.typography.labelSmall,
                    color = color.onSurfaceVariant)
                val remaining = (durationMs - shownPosition).coerceAtLeast(0L)
                Text(
                    if (knownDuration) "-" + PlaybackSeekPolicy.formatTime(remaining)
                        else stringResource(R.string.playback_duration_unknown),
                    style = MaterialTheme.typography.labelSmall,
                    color = color.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(.22f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onPrevious,
                    enabled = controlsEnabled && hasPrevious,
                    modifier = Modifier.size(60.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_action_previous),
                        contentDescription = stringResource(R.string.playback_previous),
                        modifier = Modifier.size(34.dp))
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = controlsEnabled,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.size(76.dp),
                ) {
                    Icon(
                        painterResource(if (isPlaying)
                            R.drawable.ic_action_pause else R.drawable.ic_action_play),
                        contentDescription = stringResource(if (isPlaying)
                            R.string.playback_pause else R.string.playback_resume),
                        modifier = Modifier.size(38.dp),
                    )
                }
                IconButton(
                    onClick = onNext,
                    enabled = controlsEnabled && hasNext,
                    modifier = Modifier.size(60.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_action_next),
                        contentDescription = stringResource(R.string.playback_next),
                        modifier = Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.weight(.2f))
            // A compact real action row; no inert placeholders.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = onToggleShuffle,
                    enabled = controlsEnabled && queueTotal > 0,
                ) {
                    Icon(painterResource(R.drawable.ic_action_shuffle),
                        contentDescription = stringResource(if (shuffleEnabled)
                            R.string.playback_shuffle_on else R.string.playback_shuffle_off),
                        tint = if (shuffleEnabled) color.primary else color.onSurfaceVariant)
                }
                if (queueTotal > 0) {
                    Text(
                        stringResource(R.string.now_playing_queue_short,
                            queuePosition, queueTotal),
                        style = MaterialTheme.typography.labelMedium,
                        color = color.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(
                    onClick = onCycleRepeat,
                    enabled = controlsEnabled && queueTotal > 0,
                ) {
                    Icon(painterResource(R.drawable.ic_action_repeat),
                        contentDescription = stringResource(
                            R.string.playback_repeat_control, repeatText),
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF)
                            color.primary else color.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_volume_low),
                    contentDescription = null, modifier = Modifier.size(18.dp),
                    tint = color.onSurfaceVariant)
                Slider(
                    value = volume.coerceIn(0f, 1f),
                    onValueChange = { next ->
                        volume = next
                        audio.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (next * maxVolume).toInt().coerceIn(0, maxVolume),
                            0,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                Icon(painterResource(R.drawable.ic_volume_high),
                    contentDescription = null, modifier = Modifier.size(18.dp),
                    tint = color.onSurfaceVariant)
            }
        }
    }
}
