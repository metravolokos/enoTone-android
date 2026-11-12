package app.enotone.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

internal class SettingsViewModelFactory(
    private val container: AppContainer,
    private val session: SettingsWorkSession,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = with(container) {
        val model = when (modelClass) {
            LibraryIndexingViewModel::class.java -> LibraryIndexingViewModel(
                libraryIndexingGateway, backgroundWorkLauncher, session, settingsText)
            ArtworkSettingsViewModel::class.java -> ArtworkSettingsViewModel(
                artworkSettingsGateway, backgroundWorkLauncher, session, settingsText)
            DiagnosticsViewModel::class.java -> DiagnosticsViewModel(
                diagnosticsGateway, backgroundWorkLauncher, session, settingsText)
            else -> error("Unknown settings ViewModel")
        }
        model as T
    }
}

internal object SettingsSessionFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == SettingsSessionViewModel::class.java)
        return SettingsSessionViewModel() as T
    }
}
