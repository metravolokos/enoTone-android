package app.enotone.android

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.enotone.playback.PlaybackSeekPolicy

/**
 * Screenshot-reference music sheet: real cover from the existing image cache,
 * saturated soft artwork background, stable controls and placeholder actions.
 * Never touches the Media3 queue, WebDAV or Room directly.
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
    DisposableEffect(Unit) {
        UiAppearance.showNowPlaying(true)
        onDispose { UiAppearance.showNowPlaying(false) }
    }
    val seekLabel = stringResource(R.string.playback_seek_slider)
    val closeLabel = stringResource(R.string.library_player_close)
    val hasDuration = PlaybackSeekPolicy.isKnownDuration(durationMs)
    var isScrubbing by remember(trackId) { mutableStateOf(false) }
    var scrubFraction by remember(trackId) { mutableFloatStateOf(0f) }
    val fraction = if (isScrubbing) scrubFraction
        else PlaybackSeekPolicy.fraction(positionMs, durationMs)
    val position = if (isScrubbing)
        PlaybackSeekPolicy.positionForFraction(durationMs, scrubFraction) ?: 0L
        else positionMs
    val isPlaying = playbackState == "PLAYING" || playbackState == "BUFFERING"
    val status = when (playbackState) {
        "PAUSED" -> stringResource(R.string.playback_paused)
        "BUFFERING" -> stringResource(R.string.playback_buffering)
        "ERROR" -> stringResource(R.string.playback_error, errorCode)
        "MISSING_PASSWORD" -> stringResource(R.string.playback_missing_password)
        "MISSING_TRACK" -> stringResource(R.string.playback_missing_track)
        else -> ""
    }
    val sampled = remember(artwork) { artworkColors(artwork) }
    val primary by animateColorAsState(sampled.first, tween(700), label = "primaryArtworkColor")
    val secondary by animateColorAsState(sampled.second, tween(700), label = "secondaryArtworkColor")
    val base = lerp(primary, Color.Black, 0.40f)
    val coverScale by animateFloatAsState(
        if (playbackState == "PAUSED") 0.84f else 1f,
        tween(270), label = "pausedAlbumScale",
    )
    val white = Color.White
    val muted = Color.White.copy(alpha = 0.67f)
    val rail = Color.White.copy(alpha = 0.34f)
    val hasError = playbackState in setOf("ERROR", "MISSING_PASSWORD", "MISSING_TRACK")
    var menuOpen by remember { mutableStateOf(false) }
    val repeatText = when (repeatMode) {
        Player.REPEAT_MODE_ONE -> stringResource(R.string.playback_repeat_one)
        Player.REPEAT_MODE_ALL -> stringResource(R.string.playback_repeat_all)
        else -> stringResource(R.string.playback_repeat_off)
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(base)
            .drawBehind {
                val wide = maxOf(size.width, size.height)
                drawRect(Brush.radialGradient(
                    listOf(primary.copy(alpha = .78f), Color.Transparent),
                    center = Offset(size.width * .17f, size.height * .12f),
                    radius = wide * .86f,
                ))
                drawRect(Brush.radialGradient(
                    listOf(secondary.copy(alpha = .61f), Color.Transparent),
                    center = Offset(size.width * .93f, size.height * .62f),
                    radius = wide * .72f,
                ))
                drawRect(Brush.radialGradient(
                    listOf(lerp(primary, secondary, .5f).copy(alpha = .42f), Color.Transparent),
                    center = Offset(size.width * .12f, size.height * .96f),
                    radius = wide * .72f,
                ))
                drawRect(Color.Black.copy(alpha = .20f))
            },
    ) {
        // Dimensions follow the Android screenshot reference, not an iOS
        // point-to-pixel conversion. The gallery shrinks at low available height.
        val coverSide = minOf(maxWidth - 54.dp, maxHeight * .37f, 304.dp)
        val compact = maxHeight < 720.dp
        val artGap = if (compact) 14.dp else 30.dp
        val lowerGap = if (compact) 10.dp else 22.dp
        var dragDistance by remember { mutableFloatStateOf(0f) }
        Column(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragDistance = 0f },
                        onVerticalDrag = { change, distance ->
                            dragDistance += distance
                            change.consume()
                        },
                        onDragEnd = { if (dragDistance > 130f) onClose() },
                    )
                }
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Narrow sheet grabber. Tapping it also closes the sheet.
            Box(
                modifier = Modifier.fillMaxWidth().height(44.dp)
                    .clickable(onClick = onClose)
                    .semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = .40f)))
            }
            Spacer(Modifier.weight(if (compact) .12f else .20f))
            Box(
                modifier = Modifier.size(coverSide + 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(coverSide)
                        .graphicsLayer { scaleX = coverScale; scaleY = coverScale }
                        .shadow(
                            elevation = 28.dp,
                            shape = RoundedCornerShape(8.dp),
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = .86f),
                            spotColor = Color.Black.copy(alpha = .90f),
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = .13f)),
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
                            tint = muted,
                            modifier = Modifier.size(66.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(artGap))
            Row(
                modifier = Modifier.fillMaxWidth().height(62.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title, color = white, fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, lineHeight = 25.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        artist?.takeIf { it.isNotBlank() }
                            ?: album.orEmpty(),
                        color = muted, fontSize = 20.sp, lineHeight = 25.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                // Favorites are not indexed yet: keep the correct silhouette.
                IconButton(enabled = false, onClick = {}, modifier = Modifier
                    .size(36.dp).background(Color.White.copy(alpha = .18f), CircleShape)) {
                    Icon(painterResource(R.drawable.ic_music_star),
                        contentDescription = null, tint = white,
                        modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.size(10.dp))
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier
                        .size(36.dp).background(Color.White.copy(alpha = .18f), CircleShape)) {
                        Icon(painterResource(R.drawable.ic_action_more),
                            contentDescription = stringResource(R.string.album_actions),
                            tint = white)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(painterResource(R.drawable.ic_action_shuffle),
                                contentDescription = null) },
                            text = { Text(stringResource(if (shuffleEnabled)
                                R.string.playback_shuffle_on else R.string.playback_shuffle_off)) },
                            onClick = { menuOpen = false; onToggleShuffle() },
                            enabled = controlsEnabled,
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(painterResource(R.drawable.ic_action_repeat),
                                contentDescription = null) },
                            text = { Text(repeatText) },
                            onClick = { menuOpen = false; onCycleRepeat() },
                            enabled = controlsEnabled,
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.now_playing_queue_short,
                                    queuePosition, queueTotal))
                            },
                            onClick = { menuOpen = false },
                            enabled = false,
                        )
                    }
                }
            }
            if (hasError) {
                Text(status, color = white, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(lowerGap))
            MusicThinSlider(
                value = fraction,
                enabled = canSeek && controlsEnabled && hasDuration,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = seekLabel
                },
                onValueChange = { scrubFraction = it; isScrubbing = true },
                onFinished = {
                    if (canSeek && controlsEnabled && hasDuration) onSeekFraction(scrubFraction)
                    isScrubbing = false
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(PlaybackSeekPolicy.formatTime(position), color = muted,
                    fontSize = 12.sp)
                // The quality capsule is a visual placeholder until codecs are
                // passed from Media3; it does not claim a measured audio format.
                Box(Modifier.background(Color.White.copy(alpha = .10f),
                    RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text("Lossless", color = muted, fontSize = 11.sp)
                }
                val remaining = (durationMs - position).coerceAtLeast(0L)
                Text(
                    if (hasDuration) "-"+ PlaybackSeekPolicy.formatTime(remaining)
                        else stringResource(R.string.playback_duration_unknown),
                    color = muted, fontSize = 12.sp,
                )
            }
            Spacer(Modifier.weight(if (compact) .14f else .36f))
            Row(
                modifier = Modifier.fillMaxWidth().height(76.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onPrevious, enabled = controlsEnabled && hasPrevious,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_action_previous),
                        contentDescription = stringResource(R.string.playback_previous),
                        tint = white, modifier = Modifier.size(42.dp))
                }
                IconButton(
                    onClick = onPlayPause, enabled = controlsEnabled,
                    modifier = Modifier.size(72.dp).semantics { stateDescription = status },
                ) {
                    Icon(
                        painterResource(if (isPlaying)
                            R.drawable.ic_action_pause else R.drawable.ic_action_play),
                        contentDescription = stringResource(if (isPlaying)
                            R.string.playback_pause else R.string.playback_resume),
                        tint = white, modifier = Modifier.size(60.dp))
                }
                IconButton(
                    onClick = onNext, enabled = controlsEnabled && hasNext,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_action_next),
                        contentDescription = stringResource(R.string.playback_next),
                        tint = white, modifier = Modifier.size(42.dp))
                }
            }
            Spacer(Modifier.weight(if (compact) .22f else .44f))
            Row(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Placeholder surfaces mirror Apple Music: functionality is not
                // misrepresented by a fake lyrics/cast/queue sheet.
                IconButton(onClick = {}, enabled = false, modifier = Modifier.size(52.dp)) {
                    Icon(painterResource(R.drawable.ic_music_lyrics),
                        contentDescription = null, tint = white.copy(alpha = .78f),
                        modifier = Modifier.size(25.dp))
                }
                IconButton(onClick = {}, enabled = false, modifier = Modifier.size(52.dp)) {
                    Icon(painterResource(R.drawable.ic_music_cast),
                        contentDescription = null, tint = white.copy(alpha = .78f),
                        modifier = Modifier.size(25.dp))
                }
                IconButton(onClick = { menuOpen = true }, enabled = controlsEnabled,
                    modifier = Modifier.size(52.dp)) {
                    Icon(painterResource(R.drawable.ic_music_queue),
                        contentDescription = stringResource(R.string.playback_repeat_control, repeatText),
                        tint = white.copy(alpha = .8f), modifier = Modifier.size(25.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
