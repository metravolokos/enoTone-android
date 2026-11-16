// Track/Thumb слайдера — экспериментальный API M3; нужен для тонкой линии макета 01_now_playing.
@file:OptIn(ExperimentalMaterial3Api::class)

package app.enotone.android

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.android.core.ui.component.enoShadow
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.android.core.ui.theme.EnoElevation
import app.enotone.android.core.ui.theme.EnoRadii
import app.enotone.playback.PlaybackSeekPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Material 3 Expressive / Graphite player. No artwork-sourced colors, fake
 * quality indicators, favorites, lyrics, cast, queue sheet or disabled icons.
 * All visible actions call the existing Media3 service. No in-app volume
 * control: system volume keys only (owner decision 2026-11-12, REQUIREMENTS O-12).
 */
// Существующее дерево D3 сохраняем для точных Roborazzi; ветвления Media3 не меняем в задаче движения.
@Suppress("CyclomaticComplexMethod")
@Composable
fun FeatureNowPlayingScreen(
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
    bufferedFraction: Float = 0f,
    hasKnownBufferDuration: Boolean = false,
    nextTrackState: NowPlayingNextTrackState = NowPlayingNextTrackState.NONE,
    onClose: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    ids: NowPlayingIds,
) {
    val color = MaterialTheme.colorScheme

    val knownDuration = PlaybackSeekPolicy.isKnownDuration(durationMs)
    var scrubbing by remember(trackId) { mutableStateOf(false) }
    var scrubFraction by remember(trackId) { mutableFloatStateOf(0f) }
    val shownFraction = if (scrubbing) scrubFraction
        else PlaybackSeekPolicy.fraction(positionMs, durationMs)
    val shownPosition = if (scrubbing)
        PlaybackSeekPolicy.positionForFraction(durationMs, scrubFraction) ?: 0L
        else positionMs
    val knownBufferDuration = hasKnownBufferDuration && durationMs > 0L
    val safeBufferedFraction = bufferedFraction.coerceIn(0f, 1f)
    val seekContentDescription = if (knownBufferDuration) {
        stringResource(
            ids.nowPlayingSeekKnown,
            PlaybackSeekPolicy.formatTime(shownPosition),
            PlaybackSeekPolicy.formatTime(durationMs),
            (safeBufferedFraction * BUFFERED_PERCENT_MULTIPLIER).roundToInt(),
        )
    } else {
        stringResource(ids.nowPlayingSeekUnknown, PlaybackSeekPolicy.formatTime(shownPosition))
    }
    val context = LocalContext.current
    val motionScale = Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
    )
    val loadingPulse = nextTrackState == NowPlayingNextTrackState.LOADING &&
        NextTrackLoadingMotion.shouldPulse(motionScale)
    val pulseAlpha = if (loadingPulse) {
        val pulseTransition = rememberInfiniteTransition(label = "nextTrackLoading")
        val animatedPulseAlpha by pulseTransition.animateFloat(
            initialValue = LOADING_MIN_ALPHA,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(LOADING_HALF_PERIOD_MS, easing = loadingEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "nextTrackLoadingAlpha",
        )
        NextTrackLoadingMotion.alpha(motionScale, animatedPulseAlpha)
    } else {
        NextTrackLoadingMotion.alpha(motionScale, NO_MOTION_LOADING_ALPHA)
    }
    val bufferOverlayColor = EnoColors.textSecondary.copy(alpha = BUFFER_LAYER_ALPHA)
    val readyGlowColor = EnoColors.accentText.copy(alpha = READY_GLOW_ALPHA)
    val isPlaying = playbackState == "PLAYING" || playbackState == "BUFFERING"
    val realError = when (playbackState) {
        "ERROR" -> stringResource(ids.playbackError, errorCode)
        "MISSING_PASSWORD" -> stringResource(ids.playbackMissingPassword)
        "MISSING_TRACK" -> stringResource(ids.playbackMissingTrack)
        else -> null
    }
    val repeatText = when (repeatMode) {
        Player.REPEAT_MODE_ONE -> stringResource(ids.playbackRepeatOne)
        Player.REPEAT_MODE_ALL -> stringResource(ids.playbackRepeatAll)
        else -> stringResource(ids.playbackRepeatOff)
    }
    val coverScale by animateFloatAsState(
        targetValue = if (playbackState == "PAUSED") 0.84f else 1f,
        animationSpec = tween(260), label = "pausedGraphiteCover",
    )

    // Жест обновляет только graphicsLayer, без рекомпозиции дерева плеера на каждом пикселе.
    val dragOffset = remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val settleJob = remember { mutableStateOf<Job?>(null) }
    var playerHeightPx by remember { mutableFloatStateOf(1f) }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().testTag("now-playing-gesture-root")
            .graphicsLayer {
                translationY = dragOffset.floatValue
            }
            .background(Brush.verticalGradient(listOf(color.surfaceContainerHigh, color.background)))
            .onSizeChanged { playerHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            // Slider сохраняет горизонтальное управление seek, корневой жест — вертикальный.
            .pointerInput(onClose, playerHeightPx) {
                var velocity = VelocityTracker()
                fun settle() {
                    settleJob.value?.cancel()
                    settleJob.value = scope.launch {
                        Animatable(dragOffset.floatValue).animateTo(
                            0f, animationSpec = tween(RETURN_DURATION_MS),
                        ) { dragOffset.floatValue = value }
                    }
                }
                detectVerticalDragGestures(
                    onDragStart = {
                        settleJob.value?.cancel()
                        dragOffset.floatValue = 0f
                        velocity = VelocityTracker()
                    },
                    onVerticalDrag = { change, distance ->
                        dragOffset.floatValue = (dragOffset.floatValue + distance).coerceAtLeast(0f)
                        velocity.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                    },
                    onDragEnd = {
                        if (dragOffset.floatValue >= playerHeightPx * COLLAPSE_THRESHOLD_FRACTION ||
                            velocity.calculateVelocity().y > FAST_SWIPE_PX_PER_SECOND) {
                            onClose()
                        } else {
                            settle()
                        }
                    },
                    onDragCancel = { settle() },
                )
            },
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
                // D3.2 (владелец): ручки нет — экран закрывается только жестом вниз и системным «Назад».
                Spacer(Modifier.size(48.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(ids.nowPlayingTitle),
                        style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.weight(.25f))
            Box(
                modifier = Modifier.size(coverSide + 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                // D3.2 (владелец): тень крупнее и плотнее, тонкая обводка — белая в «Графите»,
                // чёрная в «Фарфоре» (палитра v1.5). Тень рисуется под обложкой и не обрезается.
                Box(
                    modifier = Modifier.size(coverSide)
                        .graphicsLayer {
                            scaleX = coverScale
                            scaleY = coverScale
                        }
                        .enoShadow(EnoElevation.nowPlaying, EnoColors.shadowStrong, EnoRadii.l)
                        .clip(RoundedCornerShape(EnoRadii.l))
                        .background(color.surfaceVariant)
                        .border(EnoElevation.coverBorder, EnoColors.coverRim, RoundedCornerShape(EnoRadii.l)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (artwork != null) {
                        Image(
                            bitmap = artwork.asImageBitmap(),
                            contentDescription = stringResource(ids.artworkDescription),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(painterResource(ids.icNavAlbums),
                            contentDescription = stringResource(ids.nowPlayingNoArtwork),
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
            // D5 (01_now_playing): Slider retains its 48dp gesture target; the
            // reserved 20dp right slot places the dot center 16dp after the track.
            val sliderColors = SliderDefaults.colors(
                thumbColor = EnoColors.text,
                activeTrackColor = EnoColors.text,
                inactiveTrackColor = EnoColors.line,
                disabledThumbColor = EnoColors.textSecondary,
                disabledActiveTrackColor = EnoColors.textSecondary,
                disabledInactiveTrackColor = EnoColors.line,
            )
            val sliderInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    colors = sliderColors,
                    interactionSource = sliderInteraction,
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = sliderInteraction,
                            modifier = Modifier.offset(y = TRACK_HEIGHT / 2),
                            colors = sliderColors,
                            enabled = controlsEnabled && canSeek && knownDuration,
                            thumbSize = DpSize(THUMB_SIZE, THUMB_SIZE),
                        )
                    },
                    track = { sliderState ->
                        val trackShape = RoundedCornerShape(TRACK_HEIGHT / 2)
                        Box(
                            modifier = Modifier.fillMaxWidth().height(TRACK_HEIGHT)
                                .clip(trackShape),
                        ) {
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                colors = sliderColors,
                                enabled = controlsEnabled && canSeek && knownDuration,
                                thumbTrackGapSize = 0.dp,
                                drawStopIndicator = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (knownBufferDuration) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val played = sliderState.value.coerceIn(0f, 1f) * size.width
                                    val buffered = maxOf(
                                        played,
                                        safeBufferedFraction.coerceIn(0f, 1f) * size.width,
                                    )
                                    if (buffered > played) {
                                        drawRoundRect(
                                            color = bufferOverlayColor,
                                            topLeft = Offset(played, 0f),
                                            size = Size(buffered - played, size.height),
                                            cornerRadius = CornerRadius(size.height / 2f),
                                        )
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = SEEK_TOUCH_HEIGHT)
                        .semantics { contentDescription = seekContentDescription },
                )
                Box(
                    modifier = Modifier.width(DOT_GAP_WIDTH).height(SEEK_TOUCH_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    when (nextTrackState) {
                        NowPlayingNextTrackState.NONE -> Unit
                        NowPlayingNextTrackState.LOADING,
                        NowPlayingNextTrackState.READY -> {
                            val ready = nextTrackState == NowPlayingNextTrackState.READY
                            val dotColor = if (ready) {
                                EnoColors.accentText
                            } else {
                                EnoColors.textSecondary
                            }
                            val dotDescription = stringResource(
                                if (ready) ids.nowPlayingNextReady else ids.nowPlayingNextLoading,
                            )
                            Box(
                                modifier = Modifier.size(NEXT_DOT_SIZE)
                                    .drawBehind {
                                        if (ready) {
                                            drawCircle(
                                                color = readyGlowColor,
                                                radius = size.minDimension / 2f + READY_GLOW_RADIUS.toPx(),
                                            )
                                        }
                                    }
                                    .background(dotColor, CircleShape)
                                    .graphicsLayer { alpha = if (ready) 1f else pulseAlpha }
                                    .semantics { contentDescription = dotDescription },
                            )
                        }
                    }
                }
            }
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
                        else stringResource(ids.playbackDurationUnknown),
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
                    Icon(painterResource(ids.icActionPrevious),
                        contentDescription = stringResource(ids.playbackPrevious),
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
                            ids.icActionPause else ids.icActionPlay),
                        contentDescription = stringResource(if (isPlaying)
                            ids.playbackPause else ids.playbackResume),
                        modifier = Modifier.size(38.dp),
                    )
                }
                IconButton(
                    onClick = onNext,
                    enabled = controlsEnabled && hasNext,
                    modifier = Modifier.size(60.dp),
                ) {
                    Icon(painterResource(ids.icActionNext),
                        contentDescription = stringResource(ids.playbackNext),
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
                // D3.1 (01_now_playing): значки без розовой заливки; включённое перемешивание — значок
                // акцентом в круге surface2, включённый повтор — значок акцентом (повтор одного — свой значок).
                NowPlayingToggle(
                    icon = EnoIcons.SHUFFLE,
                    active = shuffleEnabled,
                    circleWhenActive = true,
                    enabled = controlsEnabled && queueTotal > 0,
                    description = stringResource(if (shuffleEnabled)
                        ids.playbackShuffleOn else ids.playbackShuffleOff),
                    onClick = onToggleShuffle,
                )
                if (queueTotal > 0) {
                    Text(
                        stringResource(ids.nowPlayingQueueShort,
                            queuePosition, queueTotal),
                        style = MaterialTheme.typography.labelMedium,
                        color = color.onSurfaceVariant,
                    )
                }
                NowPlayingToggle(
                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) EnoIcons.REPEAT_ONE else EnoIcons.REPEAT,
                    active = repeatMode != Player.REPEAT_MODE_OFF,
                    circleWhenActive = false,
                    enabled = controlsEnabled && queueTotal > 0,
                    description = stringResource(ids.playbackRepeatControl, repeatText),
                    onClick = onCycleRepeat,
                )
            }
        }
    }
}

@Composable
private fun NowPlayingToggle(
    icon: EnoIcons,
    active: Boolean,
    circleWhenActive: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(48.dp)
            .clip(CircleShape)
            .background(if (active && circleWhenActive) EnoColors.surface2 else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon.outline),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = when {
                !enabled -> EnoColors.textSecondary
                active -> EnoColors.accentText
                else -> EnoColors.text
            },
        )
    }
}

private val THUMB_SIZE = 12.dp
private val TRACK_HEIGHT = 4.dp
private val SEEK_TOUCH_HEIGHT = 48.dp
private val DOT_GAP_WIDTH = 20.dp
private val NEXT_DOT_SIZE = 8.dp
private val READY_GLOW_RADIUS = 3.dp
private const val BUFFER_LAYER_ALPHA = 0.38f
private const val READY_GLOW_ALPHA = 0.22f
private const val LOADING_MIN_ALPHA = 0.25f
private const val NO_MOTION_LOADING_ALPHA = 0.6f
private const val BUFFERED_PERCENT_MULTIPLIER = 100f
private const val LOADING_HALF_PERIOD_MS = 550
private val loadingEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private const val RETURN_DURATION_MS = 250
private const val COLLAPSE_THRESHOLD_FRACTION = 0.25f
private const val FAST_SWIPE_PX_PER_SECOND = 1_200f
