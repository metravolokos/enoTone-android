package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.enotone.database.RemoteTrackEntity

/** Карточка-группа настроек на текущих компонентах Graphite (ADR-0073), без ресурсов дизайна v2. */
@Composable
internal fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/**
 * Подэкран «Настройки → Диагностика»: всё техническое в одном месте, только чтение.
 * Наружу — только коды, числа и обезличенные метки; имена файлов видны лишь на устройстве.
 */
@Composable
internal fun SettingsDiagnosticsContent(
    state: DiagnosticsUiState,
    indexing: ManagedScanStatus,
    actions: DiagnosticsActions,
) {
    Text(stringResource(R.string.diagnostics_intro), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    SettingsGroup(stringResource(R.string.diagnostics_connection_title)) {
        OutlinedButton(enabled = !state.busy && state.hasLogin, onClick = { actions.inspect() }) {
            Text(stringResource(if (state.busy) R.string.running else R.string.run_probe))
        }
        if (state.probe.isNotEmpty()) Text(state.probe, style = MaterialTheme.typography.bodySmall)
    }
    SettingsGroup(stringResource(R.string.diagnostics_indexing_title)) { IndexingDiagnostics(indexing) }
    SettingsGroup(stringResource(R.string.diagnostics_search_title)) { SearchDiagnostics(state, actions) }
    SettingsGroup { PlaybackDiagnosticsPanel() }
    SettingsGroup(stringResource(R.string.tag_audit_title)) { TagAudit(state, actions) }
    if (state.message.isNotEmpty()) Text(state.message, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun IndexingDiagnostics(status: ManagedScanStatus) {
    val progress = status.progress
    if (progress == null && status.failureStage == null) {
        Text(stringResource(R.string.diagnostics_indexing_empty), style = MaterialTheme.typography.bodySmall)
        return
    }
    Text(stringResource(R.string.diagnostics_indexing_state, status.state.name),
        style = MaterialTheme.typography.bodySmall)
    progress?.let { current ->
        Text(stringResource(R.string.managed_index_report,
            current.completedBatches, current.attempted, current.savedWithTags,
            current.retryableErrors, current.permanentErrors, current.getRequests,
            current.downloadedBytes, current.remaining, current.stopCode),
            style = MaterialTheme.typography.bodySmall)
        if (current.lastErrorStage != "NONE" && status.failureStage == null) {
            Text(stringResource(R.string.managed_index_last_error,
                current.lastErrorStage, current.lastErrorReason, current.lastErrorHttpStatus),
                style = MaterialTheme.typography.bodySmall)
        }
    }
    status.failureStage?.let { stage ->
        Text(stringResource(R.string.managed_index_internal_failure, stage,
            status.failureReason ?: "UNEXPECTED"), style = MaterialTheme.typography.bodySmall)
    }
}

/** BUG поиска 2026-11-14: след последнего поиска и сравнение FTS с поиском в памяти. */
@Composable
private fun SearchDiagnostics(state: DiagnosticsUiState, actions: DiagnosticsActions) {
    val last by LibrarySearchTrace.last.collectAsState()
    Text(
        if (last.isEmpty()) stringResource(R.string.diagnostics_search_none)
        else stringResource(R.string.diagnostics_search_last, last),
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(enabled = !state.busy && state.hasLogin, onClick = { actions.searchProbe() }) {
        Text(stringResource(R.string.diagnostics_search_run))
    }
    if (state.searchReport.isNotEmpty()) {
        SelectionContainer { Text(state.searchReport, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Только просмотр: какие треки остались без альбома. Повторное чтение — «Переиндексировать всё…». */
@Composable
private fun TagAudit(state: DiagnosticsUiState, actions: DiagnosticsActions) {
    Text(stringResource(R.string.tag_audit_explanation), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !state.busy && state.hasLogin,
            onClick = { actions.audit(AuditQuery.MISSING_ALBUM) }) {
            Text(stringResource(R.string.tag_audit_missing_album))
        }
        OutlinedButton(enabled = !state.busy && state.hasLogin,
            onClick = { actions.audit(AuditQuery.MISSING_ALBUM_ARTIST) }) {
            Text(stringResource(R.string.tag_audit_missing_album_artist))
        }
    }
    if (state.rows.isNotEmpty()) {
        Text(stringResource(R.string.tag_audit_first_rows, state.rows.size),
            style = MaterialTheme.typography.bodySmall)
        state.rows.forEach { row -> Text(row.fileName, style = MaterialTheme.typography.bodySmall) }
    }
}

@Preview(showBackground = true)
@Composable
internal fun SettingsDiagnosticsContentPreview() {
    val row = RemoteTrackEntity("demo", "source", "/AllMusic/demo.flac", "demo.flac", null, PREVIEW_TRACK_BYTES,
        null, title = "Демонстрационный трек", lastSeenEpoch = 0)
    EnoToneTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsDiagnosticsContent(
                DiagnosticsUiState(hasLogin = true, probe = "PROPFIND /AllMusic: HTTP 207; 120 мс",
                    auditQuery = AuditQuery.MISSING_ALBUM, rows = listOf(row)),
                ManagedScanStatus(ManagedScanState.ERROR, failureStage = "STORE", failureReason = "DATABASE"),
                object : DiagnosticsActions {},
            )
        }
    }
}

private const val PREVIEW_TRACK_BYTES = 100L
