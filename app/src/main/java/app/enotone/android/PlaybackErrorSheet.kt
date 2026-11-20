package app.enotone.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.PlaybackException
import app.enotone.android.core.ui.component.EnoErrorSheet
import app.enotone.playback.PlaybackDataIssue

/** AppShell overlay: one sheet for each transition into ERROR, not each recomposition. */
@Composable
internal fun PlaybackErrorSheetHost(
    playback: PlaybackSnapshot,
    onRetryCurrent: () -> Boolean,
) {
    var handledError by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(playback.playbackState) {
        if (playback.playbackState == "ERROR") {
            if (!handledError) {
                handledError = true
                visible = true
            }
        } else {
            handledError = false
            visible = false
        }
    }

    if (visible && playback.playbackState == "ERROR") {
        EnoErrorSheet(
            message = stringResource(playbackErrorSheetMessage(playback.errorReadIssue,
                playback.errorCode)),
            actionLabel = stringResource(R.string.error_sheet_retry),
            onAction = { if (onRetryCurrent()) visible = false },
            onDismiss = { visible = false },
        )
    }
}

/** Maps fixed diagnostic categories to approved copy; raw errors never reach the UI. */
internal fun playbackErrorSheetMessage(issue: PlaybackDataIssue, errorCode: Int): Int = when {
    issue == PlaybackDataIssue.OPEN_ACCESS -> R.string.error_sheet_cloud_access
    issue == PlaybackDataIssue.OPEN_NETWORK || issue == PlaybackDataIssue.READ_NETWORK ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        R.string.error_sheet_no_cloud
    issue in unreadablePlaybackIssues || errorCode in unreadablePlaybackErrorCodes ->
        R.string.error_sheet_unreadable
    else -> R.string.error_sheet_fallback
}

private val unreadablePlaybackIssues = setOf(
    PlaybackDataIssue.OPEN_RANGE,
    PlaybackDataIssue.OPEN_ENCODING,
    PlaybackDataIssue.OPEN_VERSION,
    PlaybackDataIssue.OPEN_LENGTH,
    PlaybackDataIssue.READ_TRUNCATED,
)

private val unreadablePlaybackErrorCodes = setOf(
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
)
