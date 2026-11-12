package app.enotone.android

import androidx.compose.foundation.layout.Column
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

@Composable
internal fun SettingsIndexingSection(state: LibraryIndexingUiState, actions: IndexingActions) {
        Text(stringResource(R.string.home_library_maintenance),
            style = MaterialTheme.typography.titleMedium)
        // One explicit action: quick listing + background metadata completion.
        // Existing current-version checkpoints are skipped, not re-downloaded.
        Button(enabled = !state.busy &&
            state.status.state != ManagedScanState.RUNNING &&
            !state.status.terminating &&
            state.hasLogin, onClick = { actions.updateLibrary() }) {
            Text(stringResource(R.string.save_local_library))
        }
        // Explicit full reindex is a DIFFERENT action from normal incremental
        // import/update. Queue tags once, invalidate their lazy artwork refs,
        // then start the already-existing resumable foreground reader.
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy && state.hasLogin &&
                state.status.state != ManagedScanState.RUNNING &&
                !state.status.terminating,
            onClick = { actions.requestReindex() },
        ) {
            Text(stringResource(R.string.library_reindex_action))
        }
        if (state.confirmReindex) {
            AlertDialog(
                onDismissRequest = { actions.cancelReindex() },
                title = { Text(stringResource(R.string.library_reindex_confirm_title)) },
                text = { Text(stringResource(R.string.library_reindex_confirm_message)) },
                confirmButton = {
                    TextButton(
                        enabled = !state.busy && state.hasLogin &&
                            state.status.state != ManagedScanState.RUNNING &&
                            !state.status.terminating,
                        onClick = { actions.confirmReindex() },
                    ) { Text(stringResource(R.string.library_reindex_confirm_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { actions.cancelReindex() }) {
                        Text(stringResource(R.string.album_refresh_cancel))
                    }
                },
            )
        }

}

@Preview
@Composable
private fun SettingsIndexingSectionPreview() {
    MaterialTheme { Column { SettingsIndexingSection(LibraryIndexingUiState(hasLogin = true), object : IndexingActions {}) } }
}

@Preview
@Composable
private fun SettingsIndexingSectionStatePreview() {
    MaterialTheme { Column { SettingsIndexingSection(LibraryIndexingUiState(busy = true), object : IndexingActions {}) } }
}
