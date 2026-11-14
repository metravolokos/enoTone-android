// Track/Thumb слайдера — экспериментальный API M3; нужен для тонкой линии макета 01_now_playing.
@file:OptIn(ExperimentalMaterial3Api::class)

package app.enotone.android

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.playback.PlaybackSeekPolicy

/**
 * Material 3 Expressive / Graphite player. No artwork-sourced colors, fake
 * quality indicators, favorites, lyrics, cast, queue sheet or disabled icons.
 * All visible actions call the existing Media3 service. No in-app volume
 * control: system volume keys only (owner decision 2026-11-12, REQUIREMENTS O-12).
 */
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

    val seekLabel = stringResource(ids.playbackSeekSlider)
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

    val collapseLabel = stringResource(ids.libraryPlayerClose)
    val collapseAfterPx = with(LocalDensity.current) { COLLAPSE_DRAG.toPx() }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(color.surfaceContainerHigh, color.background),
            ),
        )
            // D3: свайп вниз сворачивает в мини-плеер. Горизонтальное перетаскивание ползунка
            // перемотки поглощается Slider и сюда не доходит.
            .pointerInput(onClose) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { change, distance ->
                        dragged += distance
                        change.consume()
                    },
                    onDragEnd = { if (dragged > collapseAfterPx) onClose() },
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
                // Вместо стрелки «назад» — ручка «Свернуть» (D3); для TalkBack это кнопка.
                Box(
                    modifier = Modifier.size(48.dp)
                        .clickable(onClickLabel = collapseLabel, role = Role.Button, onClick = onClose)
                        .semantics { contentDescription = collapseLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(width = 36.dp, height = 5.dp)
                            .background(color.onSurfaceVariant.copy(alpha = HANDLE_ALPHA), CircleShape),
                    )
                }
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
            // D3.1 (01_now_playing): тонкая линия; сыграно и бегунок — цвет текста, остальное — surface2.
            // Слой буфера и точка следующего трека — D5 (SPEC_now_playing_buffering).
            val sliderColors = SliderDefaults.colors(
                thumbColor = EnoColors.text,
                activeTrackColor = EnoColors.text,
                inactiveTrackColor = EnoColors.surface2,
                disabledThumbColor = EnoColors.textSecondary,
                disabledActiveTrackColor = EnoColors.textSecondary,
                disabledInactiveTrackColor = EnoColors.surface2,
            )
            val sliderInteraction = remember { MutableInteractionSource() }
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
                        colors = sliderColors,
                        enabled = controlsEnabled && canSeek && knownDuration,
                        thumbSize = DpSize(THUMB_SIZE, THUMB_SIZE),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        colors = sliderColors,
                        enabled = controlsEnabled && canSeek && knownDuration,
                        thumbTrackGapSize = 0.dp,
                        drawStopIndicator = null,
                        modifier = Modifier.height(TRACK_HEIGHT),
                    )
                },
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = seekLabel
                },
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

/** Свайп вниз длиннее этого сворачивает «Сейчас играет». */
private val COLLAPSE_DRAG = 96.dp
private const val HANDLE_ALPHA = 0.5f

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

private val THUMB_SIZE = 14.dp
private val TRACK_HEIGHT = 4.dp
