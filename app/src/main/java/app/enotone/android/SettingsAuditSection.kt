package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.enotone.database.RemoteTrackEntity

@Composable
internal fun SettingsAuditSection(state: DiagnosticsUiState, actions: DiagnosticsActions) {
        Button(enabled = !state.busy && state.hasLogin, onClick = { actions.run(DiagnosticCommand.SAVED) }) {
            Text(stringResource(R.string.read_local_library))
        }
        Button(enabled = !state.busy && state.hasLogin, onClick = { actions.run(DiagnosticCommand.COVERAGE) }) {
            Text(stringResource(R.string.read_stored_tags_offline))
        }
        Text(stringResource(R.string.tag_audit_title),
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.tag_audit_explanation),
            style = MaterialTheme.typography.bodySmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy && state.hasLogin,
                onClick = { actions.audit(AuditQuery.MISSING_ALBUM) },
            ) { Text(stringResource(R.string.tag_audit_missing_album)) }
            Button(modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy && state.hasLogin,
                onClick = { actions.audit(AuditQuery.MISSING_ALBUM_ARTIST) },
            ) { Text(stringResource(R.string.tag_audit_missing_album_artist)) }
        }
        OutlinedTextField(
            value = state.search,
            onValueChange = { actions.edit(DiagnosticsEdit.Search(it)) },
            label = { Text(stringResource(R.string.tag_audit_search_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy && state.hasLogin && state.search.isNotBlank(),
            onClick = { actions.audit(AuditQuery.SEARCH) },
        ) { Text(stringResource(R.string.tag_audit_search_action)) }
        AuditRows(state, actions)
        if (state.message.isNotEmpty()) {
            Text(state.message, style = MaterialTheme.typography.bodySmall)
        }
        AuditRefreshAll(state, actions)
}

@Composable
private fun AuditRows(state: DiagnosticsUiState, actions: DiagnosticsActions) {
        if (state.rows.isNotEmpty()) {
            Text(stringResource(R.string.tag_audit_first_rows, state.rows.size),
                style = MaterialTheme.typography.bodySmall)
            state.rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.fileName, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { actions.edit(DiagnosticsEdit.Selection(row.id)) }) {
                        Text(stringResource(if (state.selectedId == row.id)
                            R.string.tag_audit_selected else R.string.tag_audit_select))
                    }
                }
            }
        }
        state.rows.firstOrNull { it.id == state.selectedId }?.let { selected ->
            Text(stringResource(R.string.tag_audit_selected_details,
                selected.title ?: "—", selected.artist ?: "—",
                selected.album ?: "—", selected.albumArtist ?: "—",
                selected.tagScanCode ?: "—"),
                style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = !state.busy && state.hasLogin &&
                    !state.indexingActive,
                onClick = { actions.queueSelected() },
            ) { Text(stringResource(R.string.tag_audit_queue_selected)) }
        }
}

@Composable
private fun AuditRefreshAll(state: DiagnosticsUiState, actions: DiagnosticsActions) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = state.confirmRefreshAll,
                onCheckedChange = { actions.edit(DiagnosticsEdit.RefreshAll(it)) })
            Text(stringResource(R.string.tag_audit_refresh_all_confirm),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall)
        }
        Button(
            enabled = !state.busy && state.confirmRefreshAll && state.hasLogin &&
                !state.indexingActive,
            onClick = { actions.queueAll() },
        ) { Text(stringResource(R.string.tag_audit_queue_all)) }
        Text(stringResource(R.string.tag_audit_queue_instructions),
            style = MaterialTheme.typography.bodySmall)
}

@Preview
@Composable
internal fun SettingsAuditSectionPreview() {
    val row = RemoteTrackEntity("demo", "source", "/AllMusic/demo.flac", "demo.flac", null, 100L, null,
        title = "Демонстрационный трек", artist = "Исполнитель", lastSeenEpoch = 0)
    MaterialTheme {
        Column {
            SettingsAuditSection(DiagnosticsUiState(hasLogin = true, rows = listOf(row), selectedId = row.id),
                object : DiagnosticsActions {})
        }
    }
}

@Preview
@Composable
internal fun SettingsAuditSectionStatePreview() {
    MaterialTheme { Column { SettingsAuditSection(DiagnosticsUiState(message = "Ошибка чтения"),
        object : DiagnosticsActions {}) } }
}
