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
import java.io.IOException

internal data class LibraryIndexingUiState(
    val status: ManagedScanStatus = ManagedScanStatus(),
    val busy: Boolean = false,
    val hasLogin: Boolean = false,
    val confirmReindex: Boolean = false,
    val permissionRequest: Int = 0,
    val permissionPending: Boolean = false,
    val permissionDenied: Boolean = false,
    val report: String = "",
    val importVersion: Int = 0,
    val importedTrackCount: Int? = null,
    val pendingTagCount: Int? = null,
    val operationFailure: FirstRunIndexingFailure? = null,
    val operationFailureVersion: Int = 0,
) {
    val canStart: Boolean get() = hasLogin && !busy && !status.active && !permissionPending
}

internal enum class FirstRunIndexingFailure { AUTHENTICATION, CLOUD_UNAVAILABLE, FILES_UNREADABLE }

private const val HTTP_STATUS_UNAUTHORIZED = 401
private const val HTTP_STATUS_FORBIDDEN = 403

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
    private var pendingStartFromFirstRun = false
    internal val scanIsTerminating: Boolean get() = launcher.indexing.value.terminating

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
                    mutable.update {
                        it.copy(importVersion = 0, importedTrackCount = null, pendingTagCount = null,
                            operationFailure = null)
                    }
                }
                mutable.update { it.copy(busy = work.busy, hasLogin = work.login != null, report = work.report) }
                if (pendingLogin != null && work.login != pendingLogin) {
                    mutable.update { it.copy(permissionPending = false) }
                    pendingLogin = null
                    pendingStartFromFirstRun = false
                }
            }
        }
    }

    override fun updateLibrary(fromFirstRun: Boolean): Boolean {
        if (!allowed()) return false
        return perform(R.string.local_store_failed) { login ->
            val result = gateway.update(login)
            session.report(result.report)
            mutable.update {
                it.copy(importVersion = it.importVersion + 1,
                    importedTrackCount = result.presentTracks, pendingTagCount = result.pendingTags,
                    operationFailure = null)
            }
            if (result.pendingTags > 0) requestStart(login, fromFirstRun)
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

    override fun resume(fromFirstRun: Boolean): Boolean {
        if (!allowed()) return false
        return requestStart(requireNotNull(session.state.value.login), fromFirstRun)
    }

    /** Разрешение запрашивает Activity; Intent и секреты остаются вне Compose. */
    fun onNotificationPermission(granted: Boolean) {
        val login = pendingLogin ?: return
        val fromFirstRun = pendingStartFromFirstRun
        mutable.update { it.copy(permissionPending = false) }
        pendingLogin = null
        pendingStartFromFirstRun = false
        mutable.update { it.copy(permissionDenied = !granted) }
        if (granted || fromFirstRun) {
            val available = !launcher.indexing.value.active && !session.state.value.busy
            // Android permits foreground indexing without the notification only during first run.
            if (login == session.state.value.login && available) {
                try {
                    launcher.startIndexing(login)
                } catch (_: RuntimeException) {
                    session.report(text.get(R.string.managed_index_failed))
                    mutable.publishFailure(FirstRunIndexingFailure.FILES_UNREADABLE)
                }
            }
        }
    }

    override fun control(action: WorkAction) {
        try {
            launcher.indexingAction(action)
        } catch (_: RuntimeException) {
            session.report(text.get(R.string.managed_index_failed))
        }
    }

    private fun allowed(): Boolean {
        if (session.state.value.login == null) session.report(text.get(R.string.missing_credentials))
        return session.state.value.login != null && !session.state.value.busy &&
            !launcher.indexing.value.active && pendingLogin == null
    }

    private fun requestStart(login: StoredWebDavLogin, fromFirstRun: Boolean = false): Boolean {
        if (login != session.state.value.login) return false
        pendingLogin = login
        pendingStartFromFirstRun = fromFirstRun
        mutable.update { it.copy(permissionRequest = it.permissionRequest + 1, permissionPending = true) }
        return true
    }

    @Suppress("TooGenericExceptionCaught") // Gateway/parser failures must leave the import in an explicit failed state.
    private fun perform(failure: Int, action: suspend (StoredWebDavLogin) -> Unit): Boolean {
        val work = session.begin() ?: return false
        val login = work.login
        if (login == null) {
            session.finish(work.revision)
        } else {
            operationLogin = login
            mutable.update { it.copy(operationFailure = null) }
            operation = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    action(login)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failureException: FlatWebDavHttpError) {
                    // HTTP-код виден в «Диагностике» → «Проверить соединение».
                    // На основном экране показывается человеческий текст.
                    session.report(text.get(R.string.library_update_network_failed), work.revision)
                    mutable.publishFailure(classifyFirstRunIndexingFailure(failureException))
                } catch (failureException: IOException) {
                    session.report(text.get(R.string.library_update_network_failed), work.revision)
                    mutable.publishFailure(classifyFirstRunIndexingFailure(failureException))
                } catch (failureException: RuntimeException) {
                    session.report(text.get(failure), work.revision)
                    mutable.publishFailure(classifyFirstRunIndexingFailure(failureException))
                } finally {
                    session.finish(work.revision)
                }
            }
        }
        return login != null
    }
}

/** Reuses a saved checkpoint and rejects retries while the service is still terminating. */
internal fun LibraryIndexingViewModel.retryFirstRun(): Boolean {
    val current = state.value
    return when {
        current.permissionPending || current.status.terminating || scanIsTerminating -> false
        current.importVersion > 0 && (current.pendingTagCount ?: 0) > 0 -> resume(fromFirstRun = true)
        else -> updateLibrary(fromFirstRun = true)
    }
}

private fun MutableStateFlow<LibraryIndexingUiState>.publishFailure(failure: FirstRunIndexingFailure) {
    update { it.copy(operationFailure = failure, operationFailureVersion = it.operationFailureVersion + 1) }
}

private fun classifyFirstRunIndexingFailure(failure: Exception): FirstRunIndexingFailure = when (failure) {
    is FlatWebDavHttpError -> if (failure.statusCode in setOf(HTTP_STATUS_UNAUTHORIZED, HTTP_STATUS_FORBIDDEN)) {
        FirstRunIndexingFailure.AUTHENTICATION
    } else {
        FirstRunIndexingFailure.CLOUD_UNAVAILABLE
    }
    is IOException -> FirstRunIndexingFailure.CLOUD_UNAVAILABLE
    else -> FirstRunIndexingFailure.FILES_UNREADABLE
}
