package app.enotone.android

internal interface IndexingActions {
    fun updateLibrary() = Unit
    fun showReindex(value: Boolean) = Unit
    fun confirmReindex() = Unit
    fun start(continuous: Boolean) = Unit
    fun control(action: WorkAction) = Unit
}

internal interface DiagnosticsActions {
    fun edit(edit: DiagnosticsEdit) = Unit
    fun inspect() = Unit
    fun run(command: DiagnosticCommand) = Unit
    fun audit(query: AuditQuery) = Unit
    fun queueSelected() = Unit
    fun queueAll() = Unit
    fun pilot(count: Int) = Unit
    fun cancelPilot() = Unit
}

internal sealed interface DiagnosticsEdit {
    data class File(val value: String) : DiagnosticsEdit
    data class Infinity(val value: Boolean) : DiagnosticsEdit
    data class Search(val value: String) : DiagnosticsEdit
    data class Selection(val id: String) : DiagnosticsEdit
    data class RefreshAll(val value: Boolean) : DiagnosticsEdit
}
