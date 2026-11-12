package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Основной экран «Медиатека»: одно действие обновления, ход работы и человеческие сообщения.
 * Коды этапов, HTTP и счётчики запросов — только в «Диагностике».
 */
@Composable
internal fun SettingsIndexingSection(state: LibraryIndexingUiState, actions: IndexingActions) {
    val running = state.status.state == ManagedScanState.RUNNING
    val idle = !state.busy && state.hasLogin && !running && !state.status.terminating
    // Одно действие: быстрый список + фоновое чтение тегов; неизменённые файлы не скачиваются заново.
    Button(modifier = Modifier.fillMaxWidth(), enabled = idle, onClick = { actions.updateLibrary() }) {
        Text(stringResource(R.string.save_local_library))
    }
    state.status.progress?.let { progress ->
        Text(stringResource(R.string.home_index_summary, progress.savedWithTags, progress.remaining),
            style = MaterialTheme.typography.bodyMedium)
    }
    if (running) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { actions.control(WorkAction.PAUSE) }) {
                Text(stringResource(R.string.managed_index_pause))
            }
            OutlinedButton(onClick = { actions.control(WorkAction.STOP) }) {
                Text(stringResource(R.string.managed_index_stop))
            }
        }
    }
    IndexingInterruption(state, actions)
    if (state.permissionDenied) {
        Text(stringResource(R.string.managed_notification_permission_denied),
            style = MaterialTheme.typography.bodySmall)
    }
    if (state.report.isNotEmpty()) {
        Text(state.report, style = MaterialTheme.typography.bodySmall)
    }
    // Полная переиндексация — отдельное действие с подтверждением, не путать с обычным обновлением.
    TextButton(enabled = idle, onClick = { actions.showReindex(true) }) {
        Text(stringResource(R.string.library_reindex_action))
    }
    if (state.confirmReindex) {
        AlertDialog(
            onDismissRequest = { actions.showReindex(false) },
            title = { Text(stringResource(R.string.library_reindex_confirm_title)) },
            text = { Text(stringResource(R.string.library_reindex_confirm_message)) },
            confirmButton = {
                TextButton(enabled = idle, onClick = { actions.confirmReindex() }) {
                    Text(stringResource(R.string.library_reindex_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { actions.showReindex(false) }) {
                    Text(stringResource(R.string.album_refresh_cancel))
                }
            },
        )
    }
}

/** Пауза, остановка или сбой: понятный текст и одна кнопка продолжения с сохранённого места. */
@Composable
private fun IndexingInterruption(state: LibraryIndexingUiState, actions: IndexingActions) {
    val message = when (state.status.state) {
        ManagedScanState.PAUSED -> R.string.settings_index_paused
        ManagedScanState.STOPPED -> R.string.settings_index_stopped
        ManagedScanState.ERROR -> R.string.settings_index_failed
        else -> null
    } ?: return
    val remaining = state.status.progress?.remaining ?: 0
    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
    if (state.status.state == ManagedScanState.ERROR) {
        Text(stringResource(R.string.settings_index_details_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state.status.progress == null || remaining > 0) {
        OutlinedButton(enabled = state.canStart, onClick = { actions.resume() }) {
            Text(stringResource(if (state.status.state == ManagedScanState.ERROR)
                R.string.settings_index_retry else R.string.settings_index_resume))
        }
    }
}

@Preview
@Composable
internal fun SettingsIndexingSectionPreview() {
    EnoToneTheme { Column { SettingsIndexingSection(LibraryIndexingUiState(hasLogin = true),
        object : IndexingActions {}) } }
}

@Preview
@Composable
internal fun SettingsIndexingSectionErrorPreview() {
    EnoToneTheme { Column { SettingsIndexingSection(LibraryIndexingUiState(hasLogin = true, status =
        ManagedScanStatus(ManagedScanState.ERROR, failureStage = "STORE", failureReason = "DATABASE")),
        object : IndexingActions {}) } }
}
