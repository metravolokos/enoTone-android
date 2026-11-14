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
    val busy: Boolean = false,
    val hasLogin: Boolean = false,
    /** Обезличенный отчёт проверки соединения: только метки операций, HTTP-коды и время. */
    val probe: String = "",
    val auditQuery: AuditQuery? = null,
    val rows: List<RemoteTrackEntity> = emptyList(),
    /** Обезличенный отчёт «Проверить поиск»: только числа и коды. */
    val searchReport: String = "",
    val message: String = "",
)

/** Подэкран «Диагностика»: проверка соединения и просмотр треков без тегов. Ничего не меняет. */
internal class DiagnosticsViewModel(
    private val gateway: DiagnosticsGateway,
    private val session: SettingsWorkSession,
    private val text: SettingsText,
) : ViewModel(), DiagnosticsActions {
    private val mutable = MutableStateFlow(DiagnosticsUiState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var activeLogin: StoredWebDavLogin? = null

    init {
        viewModelScope.launch {
            session.state.collect { work ->
                // Смена или выход из аккаунта: отменяем работу и стираем имена файлов и отчёт.
                if (work.login != activeLogin) {
                    if (activeLogin != null) {
                        job?.cancel()
                        mutable.value = DiagnosticsUiState()
                    }
                    activeLogin = work.login
                }
                mutable.update { it.copy(busy = work.busy, hasLogin = work.login != null) }
            }
        }
    }

    override fun inspect() = execute(R.string.diagnostic_failed) { login ->
        val report = DiagnosticReportSanitizer.sanitize(gateway.inspect(login))
        mutable.update { it.copy(probe = report) }
    }

    override fun audit(query: AuditQuery) = execute(R.string.tag_audit_failed) { login ->
        val rows = gateway.audit(query, login.email)
        mutable.update {
            it.copy(auditQuery = query, rows = rows,
                message = if (rows.isEmpty()) text.get(R.string.tag_audit_no_missing) else "")
        }
    }

    override fun searchProbe() = execute(R.string.diagnostic_failed) { login ->
        val report = gateway.searchProbe(login.email)
        mutable.update { it.copy(searchReport = report) }
    }

    private fun execute(failure: Int, action: suspend (StoredWebDavLogin) -> Unit) {
        if (session.state.value.login == null) {
            mutable.update { it.copy(message = text.get(R.string.missing_credentials)) }
            return
        }
        val work = session.begin() ?: return
        val login = requireNotNull(work.login)
        mutable.update { it.copy(message = "") }
        job = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                action(login)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (http: FlatWebDavHttpError) {
                mutable.update { it.copy(message = text.get(R.string.webdav_http_failed, http.statusCode)) }
            } catch (_: Exception) {
                mutable.update { it.copy(message = text.get(failure)) }
            } finally {
                session.finish(work.revision)
            }
        }
    }
}
