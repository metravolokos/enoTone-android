package app.enotone.android

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

@Composable
internal fun SettingsDiagnosticsSection(state: DiagnosticsUiState, actions: DiagnosticsActions) {
        // Playback-specific numeric telemetry has moved out of Now Playing.
        Text(stringResource(R.string.home_diagnostics_title),
            style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.file, onValueChange = actions::changeFile,
            label = { Text(stringResource(R.string.test_file)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            Checkbox(checked = state.infinity, onCheckedChange = actions::changeInfinity)
            Text(stringResource(R.string.test_infinity))
        }
        Button(enabled = !state.busy, onClick = { actions.inspect() }) {
            Text(if (state.busy) stringResource(R.string.running) else stringResource(R.string.run_probe))
        }
        Button(enabled = !state.busy && state.hasLogin, onClick = { actions.run(DiagnosticCommand.COUNT) }) {
            Text(stringResource(R.string.count_audio_files))
        }

}

@Preview
@Composable
private fun SettingsDiagnosticsSectionPreview() {
    MaterialTheme { Column { SettingsDiagnosticsSection(DiagnosticsUiState(hasLogin = true), object : DiagnosticsActions {}) } }
}

@Preview
@Composable
private fun SettingsDiagnosticsSectionStatePreview() {
    MaterialTheme { Column { SettingsDiagnosticsSection(DiagnosticsUiState(busy = true), object : DiagnosticsActions {}) } }
}
