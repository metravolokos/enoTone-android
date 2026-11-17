@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.enotone.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.android.core.ui.component.EnoModalSheetSystemBars
import app.enotone.android.core.ui.component.EnoTrackRow
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.android.core.ui.theme.EnoRadii
import app.enotone.library.OfflineTrackRow
import app.enotone.playback.PlaybackQueueSnapshot

@Composable
internal fun QueueSheet(
    visible: Boolean,
    queue: PlaybackQueueSnapshot?,
    shuffleEnabled: Boolean,
    isPlaying: Boolean,
    tracksById: Map<String, OfflineTrackRow>,
    artwork: @Composable (OfflineTrackRow) -> Unit,
    onDismiss: () -> Unit,
    onSelectPosition: (Int) -> Unit,
) {
    if (!visible || queue == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val title = stringResource(R.string.queue_sheet_title)
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val lightSystemBars = MaterialTheme.colorScheme.background.luminance() > QUEUE_LIGHT_BACKGROUND_LUMINANCE

    LaunchedEffect(queue.trackIds) {
        listState.scrollToItem((queue.currentIndex - 1).coerceAtLeast(0))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetColor,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = QUEUE_SHEET_SCRIM_ALPHA),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        shape = RoundedCornerShape(topStart = EnoRadii.xl, topEnd = EnoRadii.xl),
        dragHandle = {
            Box(
                Modifier.testTag(QUEUE_SHEET_HANDLE_TAG).padding(vertical = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(EnoColors.textSecondary),
            )
        },
    ) {
        EnoModalSheetSystemBars(sheetColor, lightSystemBars)
        Column(
            Modifier.fillMaxWidth().fillMaxHeight().navigationBarsPadding()
                .semantics { paneTitle = title }
                .testTag(QUEUE_SHEET_CONTENT_TAG),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = EnoColors.text,
                )
                Text(
                    stringResource(R.string.queue_sheet_position, queue.currentIndex + 1, queue.trackIds.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EnoColors.textSecondary,
                )
            }
            if (shuffleEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(EnoIcons.SHUFFLE.outline),
                        contentDescription = null,
                        tint = EnoColors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        stringResource(R.string.queue_sheet_shuffle_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = EnoColors.textSecondary,
                    )
                }
            }
            HorizontalDivider(color = EnoColors.line)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            ) {
                val rowsThroughCurrent = queue.currentIndex + 1
                items(
                    count = rowsThroughCurrent,
                    key = { position -> queueRowKey(position, queue.trackIds[position]) },
                ) { position ->
                    QueueTrackRow(
                        position = position,
                        queue = queue,
                        tracksById = tracksById,
                        isPlaying = isPlaying,
                        artwork = artwork,
                        onSelectPosition = onSelectPosition,
                    )
                }
                if (rowsThroughCurrent < queue.trackIds.size) {
                    item(key = QUEUE_NEXT_LABEL_KEY) {
                        Text(
                            stringResource(R.string.queue_sheet_next),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EnoColors.textSecondary,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(
                        count = queue.trackIds.size - rowsThroughCurrent,
                        key = { offset ->
                            val position = rowsThroughCurrent + offset
                            queueRowKey(position, queue.trackIds[position])
                        },
                    ) { offset ->
                        QueueTrackRow(
                            position = rowsThroughCurrent + offset,
                            queue = queue,
                            tracksById = tracksById,
                            isPlaying = isPlaying,
                            artwork = artwork,
                            onSelectPosition = onSelectPosition,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun QueueSheetVisibilityGuard(queueAvailable: Boolean, onQueueUnavailable: () -> Unit) {
    LaunchedEffect(queueAvailable) {
        if (!queueAvailable) onQueueUnavailable()
    }
}

@Composable
private fun QueueTrackRow(
    position: Int,
    queue: PlaybackQueueSnapshot,
    tracksById: Map<String, OfflineTrackRow>,
    isPlaying: Boolean,
    artwork: @Composable (OfflineTrackRow) -> Unit,
    onSelectPosition: (Int) -> Unit,
) {
    val current = position == queue.currentIndex
    val past = position < queue.currentIndex
    val track = tracksById[queue.trackIds[position]]
    EnoTrackRow(
        title = track?.title ?: stringResource(R.string.queue_sheet_unavailable_track),
        subtitle = track?.artist.orEmpty(),
        onClick = { if (!current) onSelectPosition(position) },
        onMore = {},
        moreContentDescription = "",
        playingDescription = stringResource(R.string.song_playing_description),
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(EnoRadii.s))
            .background(
                color = if (current) EnoColors.surface2 else Color.Transparent,
                shape = RoundedCornerShape(EnoRadii.s),
            )
            .graphicsLayer(alpha = if (past) QUEUE_PAST_ALPHA else 1f)
            .testTag("queue-track-$position"),
        isCurrent = current,
        isPlaying = isPlaying,
        showMore = false,
        artwork = if (track == null) null else { { artwork(track) } },
    )
}

private fun queueRowKey(position: Int, trackId: String) = "$position:$trackId"

internal const val QUEUE_SHEET_CONTENT_TAG = "queue-sheet-content"
internal const val QUEUE_SHEET_HANDLE_TAG = "queue-sheet-handle"
private const val QUEUE_NEXT_LABEL_KEY = "queue-next-label"
private const val QUEUE_SHEET_SCRIM_ALPHA = 0.4f
private const val QUEUE_PAST_ALPHA = 0.5f
private const val QUEUE_LIGHT_BACKGROUND_LUMINANCE = 0.5f
