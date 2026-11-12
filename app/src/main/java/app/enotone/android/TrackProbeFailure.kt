package app.enotone.android

import app.enotone.library.WebDavRangeReader

/** Никаких Throwable.message, сырых заголовков, ETag и путей в отчёте. */
internal class TrackProbeFailure(private val text: SettingsText, private val fast: Boolean) {
    var stage = "LOCAL_DB"
    var reader: WebDavRangeReader? = null

    fun report(): String {
        val stats = reader?.stats()
        val reason = stats?.issue?.name ?: fallbackReason()
        val message = text.get(R.string.one_tag_failed_detail, stage, reason,
            stats?.httpStatus?.toString() ?: "—", stats?.requests ?: 0, stats?.downloadedBytes ?: 0L)
        val mismatch = stats?.rangeMismatch
        return if (fast || mismatch == null) message else message + " " + text.get(R.string.one_tag_range_mismatch,
            mismatch.kind.name, mismatch.expectedStart.toString(), mismatch.expectedEnd.toString(),
            mismatch.expectedTotal.toString(), mismatch.receivedStart?.toString() ?: "—",
            mismatch.receivedEnd?.toString() ?: "—", mismatch.receivedTotal?.toString() ?: "—")
    }

    private fun fallbackReason(): String = if (fast) "FAST_PROBE_ERROR" else when (stage) {
        "EXTRACTOR" -> "PLATFORM_EXTRACTOR"
        "STORE" -> "LOCAL_DB_WRITE"
        else -> "PREPARATION"
    }
}
