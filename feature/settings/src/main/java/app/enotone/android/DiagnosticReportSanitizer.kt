package app.enotone.android

import app.enotone.network.ProbeReport

/** Не доверяем произвольному тексту ответа: наружу выходят только известные метки и числа. */
object DiagnosticReportSanitizer {
    private val operations = setOf("PROPFIND /", "PROPFIND /AllMusic")
    private val notes = setOf("Частичный ответ подтверждён", "Range не подтверждён", "Multi-Status получен",
        "Проверить HTTP-код", "Сетевая ошибка или TLS")

    // Проверка из «Диагностики» не запрашивает Depth infinity и тестовый файл: пропущенные шаги не показываем.
    fun sanitize(report: ProbeReport): String = ProbeReport(report.steps.filter { it.httpStatus != null ||
        it.note == "Сетевая ошибка или TLS" }.map { step ->
        step.copy(operation = step.operation.takeIf { it in operations } ?: "PROBE",
            note = step.note.takeIf { it in notes } ?: "Скрыто")
    }).toSanitizedText()
}
