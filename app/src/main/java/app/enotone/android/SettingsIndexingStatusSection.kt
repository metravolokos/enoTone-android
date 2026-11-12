package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsIndexingStatusSection(state: LibraryIndexingUiState, actions: IndexingActions,
    showTechnicalTools: Boolean = false,
    showIndexDetails: Boolean = false, onToggleDetails: () -> Unit = {}) {
    val managedPaused = stringResource(R.string.managed_index_paused)
    val managedStopped = stringResource(R.string.managed_index_stopped)
    val managedError = stringResource(R.string.managed_index_failed)
    Text(stringResource(R.string.managed_index_title),
        style = MaterialTheme.typography.titleMedium)
    if (showTechnicalTools) {
        Text(stringResource(R.string.managed_index_explanation),
            style = MaterialTheme.typography.bodySmall)
    }
    if (state.permissionDenied) {
        Text(stringResource(R.string.managed_notification_permission_denied),
            style = MaterialTheme.typography.bodySmall)
    }
    IndexingServiceControls(state, actions, showTechnicalTools)
    IndexingProgress(state.status, showIndexDetails, showTechnicalTools, onToggleDetails)
    if (showIndexDetails || showTechnicalTools) {
    state.status.failureStage?.let { stage ->
        Text(stringResource(R.string.managed_index_internal_failure,
            stage, state.status.failureReason ?: "UNEXPECTED"),
            style = MaterialTheme.typography.bodySmall)
    }
    }
    val managedMessage = when (state.status.state) {
        ManagedScanState.PAUSED -> managedPaused
        ManagedScanState.STOPPED -> managedStopped
        ManagedScanState.ERROR -> managedError
        else -> ""
    }
    if (managedMessage.isNotEmpty()) {
        Text(managedMessage, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun IndexingServiceControls(state: LibraryIndexingUiState, actions: IndexingActions,
    showTechnicalTools: Boolean) {
        if (showTechnicalTools) {
        Button(modifier = Modifier.fillMaxWidth(),
            enabled = state.canStart,
            onClick = { actions.start(false) }) {
            Text(stringResource(if (state.status.state in setOf(ManagedScanState.PAUSED,
                    ManagedScanState.STOPPED, ManagedScanState.COMPLETE,
                    ManagedScanState.ERROR))
                R.string.managed_index_resume else R.string.managed_index_start))
        }
        }
        if (showTechnicalTools) {
            Button(modifier = Modifier.fillMaxWidth(),
                enabled = state.canStart,
                onClick = { actions.start(true) }) {
                Text(stringResource(R.string.managed_index_all))
            }
            Text(stringResource(R.string.managed_index_all_explanation),
                style = MaterialTheme.typography.bodySmall)
        }
        if (state.status.state == ManagedScanState.RUNNING) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = state.status.state == ManagedScanState.RUNNING,
                onClick = { actions.control(WorkAction.PAUSE) }) { Text(stringResource(R.string.managed_index_pause)) }
            Button(enabled = state.status.state == ManagedScanState.RUNNING,
                onClick = { actions.control(WorkAction.STOP) }) { Text(stringResource(R.string.managed_index_stop)) }
        }
        }
}

@Composable
private fun IndexingProgress(status: ManagedScanStatus, details: Boolean, technical: Boolean,
    onToggleDetails: () -> Unit) {
        status.progress?.let { current ->
            Text(stringResource(R.string.home_index_summary,
                current.savedWithTags, current.remaining))
            OutlinedButton(onClick = onToggleDetails) {
                Text(stringResource(if (details)
                    R.string.home_hide_index_details else R.string.home_show_index_details))
            }
            if (details || technical) {
            Text(stringResource(R.string.managed_index_report,
                current.completedBatches, current.attempted, current.savedWithTags,
                current.retryableErrors, current.permanentErrors, current.getRequests,
                current.downloadedBytes, current.remaining, current.stopCode),
                style = MaterialTheme.typography.bodyMedium)
            if (current.lastErrorStage != "NONE" &&
                status.failureStage == null) {
                Text(stringResource(R.string.managed_index_last_error,
                    current.lastErrorStage, current.lastErrorReason,
                    current.lastErrorHttpStatus),
                    style = MaterialTheme.typography.bodySmall)
            }
            }
        }
}

@Preview
@Composable
internal fun SettingsIndexingStatusSectionPreview() {
    MaterialTheme { Column { SettingsIndexingStatusSection(LibraryIndexingUiState(hasLogin = true),
        object : IndexingActions {}) } }
}

@Preview
@Composable
internal fun SettingsIndexingStatusSectionStatePreview() {
    MaterialTheme { Column { SettingsIndexingStatusSection(LibraryIndexingUiState(status =
        ManagedScanStatus(ManagedScanState.ERROR, failureStage = "STORE", failureReason = "DATABASE")),
            object : IndexingActions {}) } }
}
