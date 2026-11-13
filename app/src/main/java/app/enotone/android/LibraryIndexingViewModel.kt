package app.enotone.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.enotone.network.FlatWebDavHttpError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
    private var operationLogin: StoredWebDavLogin? = null
    private var activeLogin: StoredWebDavLogin? = null
    private var pendingLogin: StoredWebDavLogin? = null

    init {
        viewModelScope.launch {
            launcher.indexing.collect { status -> mutable.update { it.copy(status = status) } }
        }
        viewModelScope.launch {
            session.state.collect { work ->
                if (work.login != activeLogin) {
                    if (operationLogin != work.login) operation?.cancel()
                    activeLogin = work.login
                    showReindex(false)
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
            if (result.pendingTags > 0) requestStart(login)
        }
    }

    override fun showReindex(value: Boolean) {
        if (!value || allowed()) mutable.update { it.copy(confirmReindex = value) }
    }

    override fun confirmReindex() {
        if (!mutable.value.confirmReindex || !allowed()) return
        showReindex(false)
        perform(R.string.library_reindex_failed) { login ->
            val queued = gateway.reindex(login)
            session.report(if (queued > 0) text.get(R.string.library_reindex_queued, queued)
                else text.get(R.string.library_reindex_empty))
            if (queued > 0) requestStart(login)
        }
    }

    override fun resume() {
        if (!allowed()) return
        requestStart(requireNotNull(session.state.value.login))
    }

    /** Разрешение запрашивает Activity; Intent и секреты остаются вне Compose. */
    fun onNotificationPermission(granted: Boolean) {
        val login = pendingLogin ?: return
        clearPending()
        mutable.update { it.copy(permissionDenied = !granted) }
        val available = !launcher.indexing.value.active && !session.state.value.busy
        if (!granted || login != session.state.value.login || !available) return
        try {
            launcher.startIndexing(login)
        } catch (_: RuntimeException) {
            session.report(text.get(R.string.managed_index_failed))
        }
    }

    override fun control(action: WorkAction) {
        try {
            launcher.indexingAction(action)
        } catch (_: RuntimeException) {
            session.report(text.get(R.string.managed_index_failed))
        }
    }

    private fun clearPending() {
        mutable.update { it.copy(permissionPending = false) }
        pendingLogin = null
    }

    private fun allowed(): Boolean {
        if (session.state.value.login == null) session.report(text.get(R.string.missing_credentials))
        return session.state.value.login != null && !session.state.value.busy &&
            !launcher.indexing.value.active && pendingLogin == null
    }

    private fun requestStart(login: StoredWebDavLogin) {
        if (login != session.state.value.login) return
        pendingLogin = login
        mutable.update { it.copy(permissionRequest = it.permissionRequest + 1, permissionPending = true) }
    }

    private fun perform(failure: Int, action: suspend (StoredWebDavLogin) -> Unit) {
        val work = session.begin() ?: return
        val login = work.login ?: return
        operationLogin = login
        operation = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                action(login)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: FlatWebDavHttpError) {
                // Код ответа виден в «Диагностике» → «Проверить соединение»; на основном экране — человеческий текст.
                session.report(text.get(R.string.library_update_network_failed), work.revision)
            } catch (_: Exception) {
                session.report(text.get(failure), work.revision)
            } finally {
                session.finish(work.revision)
            }
        }
    }
}
