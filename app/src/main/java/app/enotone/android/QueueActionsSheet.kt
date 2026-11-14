package app.enotone.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.enotone.android.core.ui.component.EnoAction
import app.enotone.android.core.ui.component.EnoActionSheet
import app.enotone.android.core.ui.component.EnoActionSheetHeader
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.library.OfflineTrackRow

@Composable
internal fun queueActions(tracks: List<OfflineTrackRow>, actions: LibraryScreenActions): List<EnoAction> =
    listOf(
        EnoAction(
            EnoIcons.PLAY_NEXT,
            stringResource(if (tracks.size == 1) R.string.queue_play_next else R.string.queue_play_next_many),
        ) { actions.onPlayNext(tracks) },
        EnoAction(EnoIcons.QUEUE_ADD, stringResource(R.string.queue_append)) {
            actions.onAppendToQueue(tracks)
        },
    )

/** Порядок переданного списка совпадает с порядком песен на экране. */
@Composable
internal fun QueueActionsSheet(
    title: String,
    tracks: List<OfflineTrackRow>,
    actions: LibraryScreenActions,
    onDismiss: () -> Unit,
    extraActions: List<EnoAction> = emptyList(),
    artwork: @Composable () -> Unit,
) {
    EnoActionSheet(
        header = EnoActionSheetHeader(title, "", artwork),
        groups = listOf(queueActions(tracks, actions), extraActions),
        onDismiss = onDismiss,
    )
}
