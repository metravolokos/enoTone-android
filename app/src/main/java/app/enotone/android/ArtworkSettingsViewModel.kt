package app.enotone.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ArtworkSettingsUiState(
    val mode: ArtworkLoadMode = ArtworkLoadMode.ON_DEMAND,
    val status: ArtworkScanStatus = ArtworkScanStatus(),
    val storedBytes: Long? = null,
    val clearBusy: Boolean = false,
    val feedback: String = "",
)

internal class ArtworkSettingsViewModel(
    private val gateway: ArtworkSettingsGateway,
    private val launcher: BackgroundWorkLauncher,
    private val session: SettingsWorkSession,
    private val text: SettingsText,
) : ViewModel() {
    private val mutable = MutableStateFlow(ArtworkSettingsUiState(mode = gateway.mode()))
    val state = mutable.asStateFlow()
    private var preparation: Job? = null
    private var visible = false

    init {
        viewModelScope.launch {
            launcher.artwork.collect { status ->
                mutable.update { it.copy(status = status) }
                if (visible) refreshSize()
            }
        }
        viewModelScope.launch {
            combine(session.state, launcher.indexing) { work, indexing -> work to indexing }
                .collect { (work, indexing) ->
                    preparation?.cancel()
                    if (work.login != null && !work.busy && !indexing.active) prepareAutomatically(work.login)
                }
        }
    }

    fun setVisible(value: Boolean) {
        visible = value
        if (value) refreshSize()
    }

    fun changeMode(mode: ArtworkLoadMode) {
        if (mode == mutable.value.mode) return
        try {
            gateway.saveMode(mode)
            mutable.update { it.copy(mode = mode) }
            preparation?.cancel()
            if (mode == ArtworkLoadMode.ON_DEMAND && launcher.artwork.value.active) {
                launcher.artworkAction(WorkAction.STOP)
            }
            session.state.value.login?.let { prepareAutomatically(it) }
        } catch (_: Exception) {
            mutable.update { it.copy(feedback = text.get(R.string.local_store_failed)) }
        }
    }

    private fun autoAllowed(login: StoredWebDavLogin): Boolean =
        mutable.value.mode == ArtworkLoadMode.AUTO_AFTER_IMPORT && !mutable.value.clearBusy &&
            !launcher.artwork.value.active && !launcher.indexing.value.active &&
            !session.state.value.busy && session.state.value.login == login

    private fun prepareAutomatically(login: StoredWebDavLogin) {
        if (!autoAllowed(login)) return
        preparation = viewModelScope.launch {
            try {
                if (gateway.tagsReady(login) && gateway.needsPreparation(login) && autoAllowed(login)) {
                    launcher.startArtwork(login)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Необязательный автообход не мешает обычному прослушиванию.
            }
        }
    }

    fun refreshSize() {
        viewModelScope.launch {
            try {
                val size = gateway.storedBytes()
                mutable.update { it.copy(storedBytes = size) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(feedback = text.get(R.string.local_store_failed)) }
            }
        }
    }

    fun clear() {
        if (launcher.artwork.value.active || mutable.value.clearBusy) return
        preparation?.cancel()
        mutable.update { it.copy(clearBusy = true, feedback = "") }
        viewModelScope.launch {
            try {
                // Автоподготовка этой VM заблокирована до завершения очистки.
                if (launcher.artwork.value.active) return@launch
                gateway.clear()
                val bytes = gateway.storedBytes()
                mutable.update { it.copy(storedBytes = bytes, feedback = text.get(R.string.artwork_cache_cleared)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(feedback = text.get(R.string.artwork_cache_clear_failed)) }
            } finally {
                mutable.update { it.copy(clearBusy = false) }
            }
        }
    }

    fun pause() = control(WorkAction.PAUSE)
    fun stop() = control(WorkAction.STOP)

    private fun control(action: WorkAction) {
        try {
            launcher.artworkAction(action)
        } catch (_: RuntimeException) {
            mutable.update { it.copy(feedback = text.get(R.string.local_store_failed)) }
        }
    }
}
