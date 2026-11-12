package app.enotone.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.enotone.database.RemoteTrackEntity
import app.enotone.network.FlatWebDavHttpError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class DiagnosticsUiState(
    val file: String = "",
    val infinity: Boolean = false,
    val busy: Boolean = false,
    val hasLogin: Boolean = false,
    val indexingActive: Boolean = false,
    val pilotRunning: Boolean = false,
    val rows: List<RemoteTrackEntity> = emptyList(),
    val selectedId: String? = null,
    val search: String = "",
    val message: String = "",
    val confirmRefreshAll: Boolean = false,
    val report: String = "",
)

internal class DiagnosticsViewModel(
    private val gateway: DiagnosticsGateway,
    private val launcher: BackgroundWorkLauncher,
    private val session: SettingsWorkSession,
    private val text: SettingsText,
) : ViewModel(), DiagnosticsActions {
    private val mutable = MutableStateFlow(DiagnosticsUiState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            session.state.collect { work ->
                if (work.login == null) {
                    job?.cancel()
                    mutable.value = DiagnosticsUiState()
                }
                mutable.update { it.copy(busy = work.busy, hasLogin = work.login != null, report = work.report) }
            }
        }
        viewModelScope.launch {
            launcher.indexing.collect { status -> mutable.update { it.copy(indexingActive = status.active) } }
        }
    }

    override fun edit(edit: DiagnosticsEdit) {
        mutable.update { state ->
            when (edit) {
                is DiagnosticsEdit.File -> state.copy(file = edit.value)
                is DiagnosticsEdit.Infinity -> state.copy(infinity = edit.value)
                is DiagnosticsEdit.Search -> state.copy(search = edit.value)
                is DiagnosticsEdit.Selection -> state.copy(selectedId = edit.id)
                is DiagnosticsEdit.RefreshAll -> state.copy(confirmRefreshAll = edit.value)
            }
        }
    }

    override fun inspect() = execute(R.string.diagnostic_failed) { login, _ ->
        DiagnosticReportSanitizer.sanitize(gateway.inspect(login, mutable.value.file, mutable.value.infinity))
    }

    override fun run(command: DiagnosticCommand) = execute(failureCode(command)) { login, _ ->
        gateway.run(command, login, mutable.value.file)
    }

    private fun failureCode(command: DiagnosticCommand): Int = when (command) {
        DiagnosticCommand.COUNT -> R.string.file_count_failed
        DiagnosticCommand.CHECKPOINT -> R.string.pilot_failed
        else -> R.string.local_store_failed
    }

    override fun audit(query: AuditQuery) = execute(R.string.tag_audit_failed, audit = true) { login, _ ->
        val rows = gateway.audit(query, login.email, mutable.value.search).toList()
        mutable.update { it.copy(rows = rows, selectedId = null) }
        text.get(if (rows.isNotEmpty()) R.string.tag_audit_select_one
            else if (query == AuditQuery.SEARCH) R.string.tag_audit_no_search else R.string.tag_audit_no_missing)
    }

    override fun queueSelected() {
        val id = mutable.value.selectedId ?: return
        if (launcher.indexing.value.active) return
        execute(R.string.tag_audit_failed, audit = true) { login, _ ->
            text.get(if (gateway.queue(login.email, id) > 0) R.string.tag_audit_queued else R.string.tag_audit_failed)
        }
    }

    override fun queueAll() {
        if (!mutable.value.confirmRefreshAll || launcher.indexing.value.active) return
        execute(R.string.tag_audit_failed, audit = true) { login, _ ->
            val count = gateway.queue(login.email, null)
            mutable.update { it.copy(confirmRefreshAll = false) }
            text.get(R.string.tag_audit_all_queued, count)
        }
    }

    override fun pilot(count: Int) {
        if (launcher.indexing.value.active) return
        execute(R.string.pilot_failed, pilot = true) { login, revision ->
            gateway.pilot(login, count) { value -> session.report(value, revision) }
        }
    }

    override fun cancelPilot() { if (mutable.value.pilotRunning) job?.cancel() }

    private fun execute(
        failure: Int,
        audit: Boolean = false,
        pilot: Boolean = false,
        action: suspend (StoredWebDavLogin, Int) -> String,
    ) {
        if (session.state.value.login == null) {
            session.report(text.get(R.string.missing_credentials))
            return
        }
        val work = session.begin() ?: return
        val login = requireNotNull(work.login)
        mutable.update { it.copy(pilotRunning = pilot, message = if (audit) "" else it.message) }
        job = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val report = action(login, work.revision)
                if (audit) mutable.update { it.copy(message = report) } else session.report(report, work.revision)
            } catch (cancelled: CancellationException) {
                if (pilot) session.report(text.get(R.string.pilot_stopped), work.revision)
                throw cancelled
            } catch (http: FlatWebDavHttpError) {
                session.report(text.get(R.string.webdav_http_failed, http.statusCode), work.revision)
            } catch (_: Exception) {
                if (audit) mutable.update { it.copy(message = text.get(failure)) }
                else session.report(text.get(failure), work.revision)
            } finally {
                mutable.update { it.copy(pilotRunning = false) }
                session.finish(work.revision)
            }
        }
    }
}
