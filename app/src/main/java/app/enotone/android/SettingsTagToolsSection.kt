package app.enotone.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun SettingsTagToolsSection(state: DiagnosticsUiState, actions: DiagnosticsActions) {
        Button(enabled = !state.busy && state.hasLogin, onClick = { actions.run(DiagnosticCommand.PLATFORM_TAG) }) {
            Text(stringResource(R.string.probe_one_audio_tag))
        }
        Button(enabled = !state.busy && state.hasLogin, onClick = { actions.run(DiagnosticCommand.FAST_TAG) }) {
            Text(stringResource(R.string.fast_probe_one_track))
        }
        Button(enabled = !state.busy && state.hasLogin, onClick = { actions.run(DiagnosticCommand.CHECKPOINT) }) {
            Text(stringResource(R.string.pilot_check_offline))
        }
        Button(enabled = !state.busy && state.hasLogin,
            onClick = { actions.pilot(PILOT_SMALL) }) {
            Text(stringResource(R.string.pilot_ten))
        }
        Button(enabled = !state.busy && state.hasLogin,
            onClick = { actions.pilot(PILOT_LARGE) }) {
            Text(stringResource(R.string.pilot_twenty))
        }
        // Explicit, bounded foreground-only resume: ≤100 candidates,
        // ≤60 GET, ≤4 MiB, 180-second soft wall-clock cap per press.
        // Checkpoints already committed in Room are never re-downloaded.
        Button(enabled = !state.busy && state.hasLogin,
            modifier = Modifier.fillMaxWidth(),
            onClick = { actions.pilot(PILOT_RESUME) }) {
            Text(stringResource(R.string.pilot_resume_bounded))
        }
        Text(stringResource(R.string.pilot_resume_explanation),
            style = MaterialTheme.typography.bodySmall)
        Button(enabled = state.pilotRunning, onClick = { actions.cancelPilot() }) {
            Text(stringResource(R.string.pilot_cancel))
        }
}

@Preview
@Composable
internal fun SettingsTagToolsSectionPreview() {
    MaterialTheme { Column { SettingsTagToolsSection(DiagnosticsUiState(hasLogin = true),
        object : DiagnosticsActions {}) } }
}

@Preview
@Composable
internal fun SettingsTagToolsSectionStatePreview() {
    MaterialTheme { Column { SettingsTagToolsSection(DiagnosticsUiState(pilotRunning = true, busy = true),
        object : DiagnosticsActions {}) } }
}

private const val PILOT_SMALL = 10
private const val PILOT_LARGE = 20
private const val PILOT_RESUME = 100
