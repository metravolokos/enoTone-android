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
private fun ArtworkDataPreview() {
    MaterialTheme { SettingsArtworkSection(ArtworkSettingsUiState(storedBytes = 0), {}, {}, {}, {}) }
}

@Preview
@Composable
private fun ArtworkLoadingPreview() {
    MaterialTheme { SettingsArtworkSection(ArtworkSettingsUiState(clearBusy = true), {}, {}, {}, {}) }
}

@Preview
@Composable
private fun ArtworkErrorPreview() {
    MaterialTheme { SettingsArtworkSection(ArtworkSettingsUiState(feedback = "Ошибка очистки"), {}, {}, {}, {}) }
}
