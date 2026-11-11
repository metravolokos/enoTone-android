package app.enotone.android

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.enotone.playback.PlaybackSeekPolicy

/**
 * Full-screen dark-on-artwork sheet in both app themes. All colors are
 * calculated from the existing in-memory album bitmap; NO WebDAV/Room/GET.
 * The existing Media3 service and queue remain the sole playback authority.
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
    // White system icons above the full-bleed artwork sheet; restore on close.
    DisposableEffect(Unit) {
        UiAppearance.setPlayerVisible(true)
        onDispose { UiAppearance.setPlayerVisible(false) }
    }
    var isScrubbing by remember(trackId) { mutableStateOf(false) }
    var scrubFraction by remember(trackId) { mutableStateOf(0f) }
    val hasDuration = PlaybackSeekPolicy.isKnownDuration(durationMs)
    val shownFraction = if (isScrubbing) scrubFraction
        else PlaybackSeekPolicy.fraction(positionMs, durationMs)
    val shownPosition = if (isScrubbing) {
        PlaybackSeekPolicy.positionForFraction(durationMs, scrubFraction) ?: 0L
    } else positionMs
    val pauseIsAvailable = playbackState == "PLAYING" || playbackState == "BUFFERING"
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
    val repeatText = when (repeatMode) {
        Player.REPEAT_MODE_ONE -> stringResource(R.string.playback_repeat_one)
        Player.REPEAT_MODE_ALL -> stringResource(R.string.playback_repeat_all)
        else -> stringResource(R.string.playback_repeat_off)
    }
    val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
    val seekLabel = stringResource(R.string.playback_seek_slider)

    val sampled = remember(artwork) { artworkColors(artwork) }
    val spot1 by animateColorAsState(sampled.first, tween(600), label = "artworkSpot1")
    val spot2 by animateColorAsState(sampled.second, tween(600), label = "artworkSpot2")
    val middleColor by animateColorAsState(
        lerp(sampled.first, sampled.second, 0.54f), tween(600), label = "artworkSpot3",
    )
    val coverScale by animateFloatAsState(
        targetValue = if (playbackState == "PAUSED") 0.84f else 1f,
        animationSpec = tween(280),
        label = "pausedArtworkScale",
    )

    // Controls and text remain white regardless of the app's light/dark mode.
    val ink = Color.White
    val secondaryInk = Color.White.copy(alpha = 0.65f)
    val quietInk = Color.White.copy(alpha = 0.45f)
    val sliderPalette = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White.copy(alpha = 0.88f),
        inactiveTrackColor = Color.White.copy(alpha = 0.30f),
        disabledThumbColor = quietInk,
        disabledActiveTrackColor = quietInk,
        disabledInactiveTrackColor = quietInk,
    )
    // The system media volume is real state. No inactive/fake volume control.
    val context = LocalContext.current
    val audio = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember(audio) {
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volume by remember(audio) {
        mutableFloatStateOf(
            audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume,
        )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .background(Color(0xFF19191D))
            .drawBehind {
                // Three broad radial spots make a softened artwork palette.
                // Never use a full-resolution image blur on every frame.
                val radius = maxOf(size.width, size.height) * 0.93f
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(spot1.copy(alpha = 0.95f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.17f),
                        radius = radius,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(spot2.copy(alpha = 0.82f), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.66f),
                        radius = radius,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(middleColor.copy(alpha = 0.52f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.92f),
                        radius = radius * 0.73f,
                    ),
                )
                // The sheet ALWAYS uses white controls, even in Light mode.
                drawRect(Color.Black.copy(alpha = 0.28f))
            },
    ) {
        // Android dp is density-independent, not identical to CSS px.
        // 334dp is an upper bound; keep all controls reachable on Huawei.
        val coverSide = minOf(
            (maxWidth - 56.dp).coerceAtLeast(160.dp),
            (maxHeight * 0.40f).coerceAtLeast(170.dp),
            334.dp,
        )
        Column(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            var dragAmount by remember { mutableFloatStateOf(0f) }
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { dragAmount = 0f },
                            onVerticalDrag = { change, distance ->
                                dragAmount += distance
                                change.consume()
                            },
                            onDragEnd = { if (dragAmount > 55f) onClose() },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_back),
                        contentDescription = stringResource(R.string.library_player_close),
                        tint = ink,
                    )
                }
                Box(
                    modifier = Modifier.weight(1f).height(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(width = 36.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.38f)),
                    )
                }
                Spacer(modifier = Modifier.size(44.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            // The halo and 32dp drop-shadow are outside the clipped artwork.
            // The allocated layout height never changes during PAUSED.
            Box(
                modifier = Modifier.size(coverSide + 20.dp)
                    .graphicsLayer { scaleX = coverScale; scaleY = coverScale },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(coverSide)
                        .shadow(
                            elevation = 32.dp,
                            shape = RoundedCornerShape(8.dp),
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.92f),
                            spotColor = Color.Black.copy(alpha = 0.90f),
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF303034)),
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
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_albums),
                            contentDescription = stringResource(R.string.now_playing_no_artwork),
                            tint = secondaryInk,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                val subtitle = artist?.takeIf { it.isNotBlank() }
                    ?: album?.takeIf { it.isNotBlank() }
                Text(
                    text = subtitle.orEmpty(),
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    color = secondaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                // The error occupies this existing area, not an extra row.
                if (playbackState in setOf("ERROR", "MISSING_PASSWORD", "MISSING_TRACK")) {
                    Text(
                        stateText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
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
                colors = sliderPalette,
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
                    color = secondaryInk,
                    fontSize = 12.sp,
                )
                val remaining = (durationMs - shownPosition).coerceAtLeast(0L)
                Text(
                    text = if (hasDuration) "-" + PlaybackSeekPolicy.formatTime(remaining)
                        else stringResource(R.string.playback_duration_unknown),
                    color = secondaryInk,
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(42.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = controlsEnabled && hasPrevious,
                    onClick = onPrevious,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_previous),
                        contentDescription = stringResource(R.string.playback_previous),
                        tint = ink,
                        modifier = Modifier.size(42.dp),
                    )
                }
                IconButton(
                    enabled = controlsEnabled,
                    onClick = onPlayPause,
                    modifier = Modifier.size(64.dp).semantics {
                        stateDescription = stateText
                    },
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
                        tint = ink,
                        modifier = Modifier.size(56.dp),
                    )
                }
                IconButton(
                    enabled = controlsEnabled && hasNext,
                    onClick = onNext,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_next),
                        contentDescription = stringResource(R.string.playback_next),
                        tint = ink,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_volume_low),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = secondaryInk,
                )
                Slider(
                    value = volume,
                    onValueChange = { newValue ->
                        volume = newValue
                        audio.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (newValue * maxVolume).toInt().coerceIn(0, maxVolume),
                            0,
                        )
                    },
                    colors = sliderPalette,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_volume_high),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = secondaryInk,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            // Only REAL controls. Lyrics, casting and queue-sheet buttons must
            // not appear until those features have a working implementation.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = controlsEnabled && queueTotal > 0,
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .background(
                            if (shuffleEnabled) Color.White.copy(alpha = 0.20f)
                            else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_shuffle),
                        contentDescription = stringResource(
                            if (shuffleEnabled) R.string.playback_shuffle_on
                            else R.string.playback_shuffle_off,
                        ),
                        tint = ink,
                    )
                }
                if (queueTotal > 0) {
                    Text(
                        text = stringResource(R.string.now_playing_queue_short,
                            queuePosition, queueTotal),
                        color = quietInk,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                IconButton(
                    enabled = controlsEnabled && queueTotal > 0,
                    onClick = onCycleRepeat,
                    modifier = Modifier.background(
                        if (repeatActive) Color.White.copy(alpha = 0.20f)
                        else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_repeat),
                        contentDescription = stringResource(
                            R.string.playback_repeat_control, repeatText,
                        ),
                        tint = ink,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
