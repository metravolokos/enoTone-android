package app.enotone.android

import app.enotone.library.OfflineTrackRow

/** Продолжительность берётся только из сохранённых тегов, без запросов к сети или Media3. */
internal object AlbumDuration {
    fun totalMinutes(tracks: List<OfflineTrackRow>): Long? {
        if (tracks.isEmpty() || tracks.any { (it.durationMs ?: 0L) <= 0L }) return null
        return tracks.sumOf { requireNotNull(it.durationMs) } / MILLIS_PER_MINUTE
    }

    /** m:ss; неизвестная длительность остаётся пустой, а не показывается как 0:00. */
    fun trackTime(durationMs: Long?): String? {
        if (durationMs == null || durationMs <= 0L) return null
        val seconds = durationMs / MILLIS_PER_SECOND
        return "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
    }

    fun albumFacts(year: Int?, genre: String?, songs: String, minutes: String?): String =
        buildList {
            year?.let { add(it.toString()) }
            genre?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            add(listOfNotNull(songs, minutes).joinToString(", "))
        }.joinToString(" · ")

    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MILLIS_PER_MINUTE = MILLIS_PER_SECOND * SECONDS_PER_MINUTE
}
