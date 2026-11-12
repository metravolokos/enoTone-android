package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Existing Graphite controls unchanged; no Room, Service or WebDAV in UI. */
@Composable
internal fun ManagedIndexSettingsSection(
    status: ManagedScanStatus,
    showTechnicalTools: Boolean,
    notificationPermissionDenied: Boolean,
    busy: Boolean,
    connected: Boolean,
    report: String,
    onStart: (Boolean) -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    var showIndexDetails by remember { mutableStateOf(false) }
    Text(stringResource(R.string.managed_index_title),
        style = MaterialTheme.typography.titleMedium)
    if (showTechnicalTools) {
        Text(stringResource(R.string.managed_index_explanation),
            style = MaterialTheme.typography.bodySmall)
    }
    if (notificationPermissionDenied) {
        Text(stringResource(R.string.managed_notification_permission_denied),
            style = MaterialTheme.typography.bodySmall)
    }
    if (showTechnicalTools) {
        Button(modifier = Modifier.fillMaxWidth(),
            enabled = !busy && status.state != ManagedScanState.RUNNING &&
                !status.terminating && connected,
            onClick = { onStart(false) }) {
            Text(stringResource(if (status.state in setOf(ManagedScanState.PAUSED,
                    ManagedScanState.STOPPED, ManagedScanState.COMPLETE,
                    ManagedScanState.ERROR))
                R.string.managed_index_resume else R.string.managed_index_start))
        }
        Button(modifier = Modifier.fillMaxWidth(),
            enabled = !busy && status.state != ManagedScanState.RUNNING &&
                !status.terminating && connected,
            onClick = { onStart(true) }) {
            Text(stringResource(R.string.managed_index_all))
        }
        Text(stringResource(R.string.managed_index_all_explanation),
            style = MaterialTheme.typography.bodySmall)
    }
    if (status.state == ManagedScanState.RUNNING) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPause) { Text(stringResource(R.string.managed_index_pause)) }
            Button(onClick = onStop) { Text(stringResource(R.string.managed_index_stop)) }
        }
    }
    status.progress?.let { current ->
        Text(stringResource(R.string.home_index_summary,
            current.savedWithTags, current.remaining))
        OutlinedButton(onClick = { showIndexDetails = !showIndexDetails }) {
            Text(stringResource(if (showIndexDetails)
                R.string.home_hide_index_details else R.string.home_show_index_details))
        }
        if (showIndexDetails || showTechnicalTools) {
            Text(stringResource(R.string.managed_index_report,
                current.completedBatches, current.attempted, current.savedWithTags,
                current.retryableErrors, current.permanentErrors, current.getRequests,
                current.downloadedBytes, current.remaining, current.stopCode),
                style = MaterialTheme.typography.bodyMedium)
            if (current.lastErrorStage != "NONE" && status.failureStage == null) {
                Text(stringResource(R.string.managed_index_last_error,
                    current.lastErrorStage, current.lastErrorReason,
                    current.lastErrorHttpStatus),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (showIndexDetails || showTechnicalTools) {
        status.failureStage?.let { stage ->
            Text(stringResource(R.string.managed_index_internal_failure,
                stage, status.failureReason ?: "UNEXPECTED"),
                style = MaterialTheme.typography.bodySmall)
        }
    }
    val message = when (status.state) {
        ManagedScanState.PAUSED -> R.string.managed_index_paused
        ManagedScanState.STOPPED -> R.string.managed_index_stopped
        ManagedScanState.ERROR -> R.string.managed_index_failed
        else -> null
    }
    if (message != null) Text(stringResource(message), style = MaterialTheme.typography.bodySmall)
    if (report.isNotEmpty()) Text(report, style = MaterialTheme.typography.bodyMedium)
}

@Preview(showBackground = true, name = "Индексация — не запущена")
@Composable
private fun PreviewManagedIndexIdle() {
    EnoToneTheme {
        ManagedIndexSettingsSection(
            status = ManagedScanStatus(), showTechnicalTools = true,
            notificationPermissionDenied = false, busy = false,
            connected = false, report = "", onStart = {}, onPause = {}, onStop = {})
    }
}

@Preview(showBackground = true, name = "Индексация — ошибка")
@Composable
private fun PreviewManagedIndexError() {
    EnoToneTheme {
        ManagedIndexSettingsSection(
            status = ManagedScanStatus(state = ManagedScanState.ERROR),
            showTechnicalTools = true, notificationPermissionDenied = false,
            busy = false, connected = true, report = "",
            onStart = {}, onPause = {}, onStop = {})
    }
}
