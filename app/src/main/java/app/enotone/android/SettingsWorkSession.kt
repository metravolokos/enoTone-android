package app.enotone.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Общая блокировка сохраняет прежнюю взаимную исключительность технических действий. */
internal data class SettingsWorkState(
    val login: StoredWebDavLogin? = null,
    val busy: Boolean = false,
    val report: String = "",
    val revision: Int = 0,
)

internal class SettingsWorkSession {
    private val mutable = MutableStateFlow(SettingsWorkState())
    val state = mutable.asStateFlow()

    fun updateLogin(login: StoredWebDavLogin?) {
        if (login != mutable.value.login) {
            mutable.update { SettingsWorkState(login = login, revision = it.revision + 1) }
        }
    }

    fun begin(): SettingsWorkState? {
        val current = mutable.value
        if (current.busy) return null
        mutable.value = current.copy(busy = true, report = "")
        return current
    }

    fun report(value: String, revision: Int = mutable.value.revision) {
        mutable.update { if (it.revision == revision) it.copy(report = value) else it }
    }

    fun finish(revision: Int) {
        mutable.update { if (it.revision == revision) it.copy(busy = false) else it }
    }
}

internal fun interface SettingsText {
    fun get(id: Int, vararg args: Any): String
}

internal val ManagedScanStatus.active: Boolean
    get() = state == ManagedScanState.RUNNING || terminating

internal val ArtworkScanStatus.active: Boolean
    get() = state == ArtworkScanState.RUNNING || terminating
