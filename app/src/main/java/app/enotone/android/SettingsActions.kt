package app.enotone.android

internal interface IndexingActions {
    fun updateLibrary() = Unit
    fun requestReindex() = Unit
    fun cancelReindex() = Unit
    fun confirmReindex() = Unit
    fun start(continuous: Boolean) = Unit
    fun pause() = Unit
    fun stop() = Unit
}

internal interface DiagnosticsActions {
    fun changeFile(value: String) = Unit
    fun changeInfinity(value: Boolean) = Unit
    fun changeSearch(value: String) = Unit
    fun select(id: String) = Unit
    fun confirmRefreshAll(value: Boolean) = Unit
    fun inspect() = Unit
    fun run(command: DiagnosticCommand) = Unit
    fun audit(query: AuditQuery) = Unit
    fun queueSelected() = Unit
    fun queueAll() = Unit
    fun pilot(count: Int) = Unit
    fun cancelPilot() = Unit
}
