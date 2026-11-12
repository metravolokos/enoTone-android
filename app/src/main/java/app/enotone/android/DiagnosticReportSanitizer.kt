package app.enotone.android

import app.enotone.network.ProbeReport

/** Не доверяем произвольному тексту ответа: наружу выходят только известные метки и числа. */
internal object DiagnosticReportSanitizer {
    private val operations = setOf("PROPFIND /", "PROPFIND /AllMusic", "PROPFIND Depth infinity", "GET Range 0-0")
    private val notes = setOf("Не запрошено", "Не проверено: нужен путь /AllMusic/…",
        "Частичный ответ подтверждён", "Range не подтверждён", "Multi-Status получен",
        "Проверить HTTP-код", "Сетевая ошибка или TLS")

    fun sanitize(report: ProbeReport): String = ProbeReport(report.steps.map { step ->
        step.copy(operation = step.operation.takeIf { it in operations } ?: "PROBE",
            note = step.note.takeIf { it in notes } ?: "Скрыто")
    }).toSanitizedText()
}
