package app.enotone.android.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.android.core.ui.theme.EnoElevation
import app.enotone.android.core.ui.theme.EnoRadii

/**
 * Закреплённый мини-плеер Android v2 (макет `02_songs`, `.mini`): обложка и название открывают
 * «Сейчас играет», ровно две кнопки — играть/пауза и «следующий», тонкий прогресс снизу.
 * [buffered] — слой буфера (D5, SPEC_now_playing_buffering); `null` — не рисуется.
 */
@Composable
fun EnoMiniPlayer(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    progress: Float,
    labels: EnoMiniPlayerLabels,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    artwork: (@Composable () -> Unit)? = null,
    buffered: Float? = null,
    playEnabled: Boolean = true,
    nextEnabled: Boolean = true,
) {
    val shape = RoundedCornerShape(EnoRadii.m)
    Box(
        modifier = modifier.fillMaxWidth().height(64.dp)
            .shadow(
                elevation = EnoElevation.floatingPlayer.blur / 2,
                shape = shape,
                ambientColor = EnoColors.shadow,
                spotColor = EnoColors.shadow,
            )
            .clip(shape)
            .dockBackground(MaterialTheme.colorScheme.surface)
            // Жест вверх по мини-плееру открывает «Сейчас играет», как раньше (#92).
            .pointerInput(onOpen) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { change, distance ->
                        dragged += distance
                        change.consume()
                    },
                    onDragEnd = { if (dragged < -OPEN_SWIPE_PX) onOpen() },
                )
            },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .clickable(onClickLabel = labels.open, role = Role.Button, onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(44.dp)
                        .clip(RoundedCornerShape(EnoRadii.xs))
                        .background(EnoColors.surface2),
                ) {
                    artwork?.invoke()
                    Box(
                        Modifier.matchParentSize()
                            .border(EnoElevation.coverBorder, EnoColors.coverOutline, RoundedCornerShape(EnoRadii.xs)),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = EnoColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = EnoColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            MiniControl(
                icon = if (isPlaying) EnoIcons.PAUSE else EnoIcons.PLAY,
                description = if (isPlaying) labels.pause else labels.play,
                enabled = playEnabled,
                onClick = onPlayPause,
            )
            MiniControl(icon = EnoIcons.NEXT, description = labels.next, enabled = nextEnabled, onClick = onNext)
        }
        MiniProgress(progress = progress, buffered = buffered, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun MiniControl(icon: EnoIcons, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp)
            .clip(EnoRadii.full)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon.outline),
            contentDescription = null,
            modifier = Modifier.size(28.dp).then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA)),
            tint = EnoColors.text,
        )
    }
}

@Composable
private fun MiniProgress(progress: Float, buffered: Float?, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp)
            .background(EnoColors.line),
    ) {
        if (buffered != null) {
            Box(
                Modifier.fillMaxWidth(buffered.coerceIn(0f, 1f)).fillMaxHeight()
                    .background(EnoColors.textSecondary.copy(alpha = BUFFER_ALPHA)),
            )
        }
        Box(
            Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight()
                .background(EnoColors.text),
        )
    }
}

private const val OPEN_SWIPE_PX = 48f
private const val BUFFER_ALPHA = 0.38f
private const val DISABLED_ALPHA = 0.38f

/** Подписи мини-плеера для TalkBack; строки RU/EN остаются в :app. */
data class EnoMiniPlayerLabels(
    val open: String,
    val play: String,
    val pause: String,
    val next: String,
)
