package app.enotone.android

internal interface IndexingActions {
    fun updateLibrary() = Unit
    fun showReindex(value: Boolean) = Unit
    fun confirmReindex() = Unit

    /** Продолжить прерванное, приостановленное или остановленное обновление с сохранённого места. */
    fun resume() = Unit

    fun control(action: WorkAction) = Unit
}

/** Подэкран «Диагностика»: только чтение, без изменения медиатеки. */
internal interface DiagnosticsActions {
    fun inspect() = Unit
    fun audit(query: AuditQuery) = Unit
}
