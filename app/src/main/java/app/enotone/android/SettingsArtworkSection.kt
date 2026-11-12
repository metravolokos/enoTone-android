package app.enotone.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun SettingsArtworkSection(
    state: ArtworkSettingsUiState,
    onModeChange: (ArtworkLoadMode) -> Unit,
    onClear: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    ArtworkSettingsPanel(state.mode, onModeChange, state.status, state.storedBytes,
        state.clearBusy, state.feedback, onClear, onPause, onStop)
}

@Preview
@Composable
internal fun ArtworkDataPreview() {
    MaterialTheme { SettingsArtworkSection(ArtworkSettingsUiState(storedBytes = 0), {}, {}, {}, {}) }
}

@Preview
@Composable
internal fun ArtworkLoadingPreview() {
    MaterialTheme { SettingsArtworkSection(ArtworkSettingsUiState(clearBusy = true), {}, {}, {}, {}) }
}

@Preview
@Composable
internal fun ArtworkErrorPreview() {
    MaterialTheme { SettingsArtworkSection(ArtworkSettingsUiState(feedback = "Ошибка очистки"), {}, {}, {}, {}) }
}

@Preview
@Composable
internal fun ArtworkRunningPreview() {
    val status = ArtworkScanStatus(ArtworkScanState.RUNNING, completed = 1, total = 2)
    MaterialTheme {
        SettingsArtworkSection(ArtworkSettingsUiState(status = status), {}, {}, {}, {})
    }
}
