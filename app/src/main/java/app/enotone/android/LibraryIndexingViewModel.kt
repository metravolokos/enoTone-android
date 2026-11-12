package app.enotone.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.enotone.library.ManagedIndexMode
import app.enotone.network.FlatWebDavHttpError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class LibraryIndexingUiState(
    val status: ManagedScanStatus = ManagedScanStatus(),
    val busy: Boolean = false,
    val hasLogin: Boolean = false,
    val confirmReindex: Boolean = false,
    val permissionRequest: Int = 0,
    val permissionPending: Boolean = false,
    val permissionDenied: Boolean = false,
    val report: String = "",
) {
    val canStart: Boolean get() = hasLogin && !busy && !status.active && !permissionPending
}

internal class LibraryIndexingViewModel(
    private val gateway: LibraryIndexingGateway,
    private val launcher: BackgroundWorkLauncher,
    private val session: SettingsWorkSession,
    private val text: SettingsText,
) : ViewModel(), IndexingActions {
    private val mutable = MutableStateFlow(LibraryIndexingUiState())
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var activeLogin: StoredWebDavLogin? = null
    private var pendingMode: ManagedIndexMode? = null
    private var pendingLogin: StoredWebDavLogin? = null

    init {
        viewModelScope.launch {
            launcher.indexing.collect { status -> mutable.update { it.copy(status = status) } }
        }
        viewModelScope.launch {
            session.state.collect { work ->
                if (work.login != activeLogin) {
                    operation?.cancel()
                    activeLogin = work.login
                    cancelReindex()
                }
                mutable.update { it.copy(busy = work.busy, hasLogin = work.login != null, report = work.report) }
                if (pendingLogin != null && work.login != pendingLogin) clearPending()
            }
        }
    }

    override fun updateLibrary() {
        if (!allowed()) return
        perform(R.string.local_store_failed) { login ->
            val result = gateway.update(login)
            session.report(result.report)
            if (result.pendingTags > 0) requestStart(login, ManagedIndexMode.CONTINUOUS)
        }
    }

    override fun requestReindex() {
        if (allowed()) mutable.update { it.copy(confirmReindex = true) }
    }

    override fun cancelReindex() { mutable.update { it.copy(confirmReindex = false) } }

    override fun confirmReindex() {
        if (!mutable.value.confirmReindex || !allowed()) return
        cancelReindex()
        perform(R.string.library_reindex_failed) { login ->
            val queued = gateway.reindex(login)
            session.report(if (queued > 0) text.get(R.string.library_reindex_queued, queued)
                else text.get(R.string.library_reindex_empty))
            if (queued > 0) requestStart(login, ManagedIndexMode.CONTINUOUS)
        }
    }

    override fun start(continuous: Boolean) {
        if (!allowed()) return
        requestStart(requireNotNull(session.state.value.login),
            if (continuous) ManagedIndexMode.CONTINUOUS else ManagedIndexMode.BOUNDED)
    }

    /** Разрешение запрашивает Activity; Intent и секреты остаются вне Compose. */
    fun onNotificationPermission(granted: Boolean) {
        val mode = pendingMode ?: return
        val login = pendingLogin
        clearPending()
        mutable.update { it.copy(permissionDenied = !granted) }
        if (!granted || login == null || login != session.state.value.login || launcher.indexing.value.active) return
        try {
            launcher.startIndexing(login, mode)
        } catch (_: RuntimeException) {
            session.report(text.get(R.string.managed_index_failed))
        }
    }

    override fun pause() = control(WorkAction.PAUSE)
    override fun stop() = control(WorkAction.STOP)

    private fun control(action: WorkAction) {
        try {
            launcher.indexingAction(action)
        } catch (_: RuntimeException) {
            session.report(text.get(R.string.managed_index_failed))
        }
    }

    private fun clearPending() {
        mutable.update { it.copy(permissionPending = false) }
        pendingMode = null
        pendingLogin = null
    }

    private fun allowed(): Boolean {
        if (session.state.value.login == null) session.report(text.get(R.string.missing_credentials))
        return session.state.value.login != null && !session.state.value.busy &&
            !launcher.indexing.value.active && pendingMode == null
    }

    private fun requestStart(login: StoredWebDavLogin, mode: ManagedIndexMode) {
        if (login != session.state.value.login) return
        pendingLogin = login
        pendingMode = mode
        mutable.update { it.copy(permissionRequest = it.permissionRequest + 1, permissionPending = true) }
    }

    private fun perform(failure: Int, action: suspend (StoredWebDavLogin) -> Unit) {
        val work = session.begin() ?: return
        val login = work.login ?: return
        operation = viewModelScope.launch {
            try {
                action(login)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (http: FlatWebDavHttpError) {
                session.report(text.get(R.string.webdav_http_failed, http.statusCode), work.revision)
            } catch (_: Exception) {
                session.report(text.get(failure), work.revision)
            } finally {
                session.finish(work.revision)
            }
        }
    }
}
