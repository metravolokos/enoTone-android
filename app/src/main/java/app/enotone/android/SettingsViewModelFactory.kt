package app.enotone.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

internal class SettingsViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = with(container) {
        val model = when (modelClass) {
            LibraryIndexingViewModel::class.java -> LibraryIndexingViewModel(
                libraryIndexingGateway, backgroundWorkLauncher, settingsWorkSession, settingsText)
            ArtworkSettingsViewModel::class.java -> ArtworkSettingsViewModel(
                artworkSettingsGateway, backgroundWorkLauncher, settingsWorkSession, settingsText)
            DiagnosticsViewModel::class.java -> DiagnosticsViewModel(
                diagnosticsGateway, backgroundWorkLauncher, settingsWorkSession, settingsText)
            else -> error("Unknown settings ViewModel")
        }
        model as T
    }
}
