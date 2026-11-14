package app.enotone.android

import app.enotone.database.EnoToneDatabase
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.LibraryText

/**
 * Проверка поиска на устройстве (BUG track search, 2026-11-14): сравнивает FTS4 с поиском
 * в памяти на одних и тех же коротких пробах. Наружу — только числа, время и класс ошибки.
 */
internal class SearchProbe(private val database: EnoToneDatabase) {
    suspend fun run(email: String): String {
        val key = LibrarySnapshotPlanner.accountKey(email)
        val tracks = database.remoteTrackDao().allForSource(key).filter { it.missingScans == 0 }
        val indexRows = database.trackSearchDao().allForSource(key).size
        val lines = mutableListOf(
            "sqlite=${sqliteVersion()} tracks=${tracks.size} index=$indexRows",
        )
        val haystacks = tracks.map { row ->
            listOfNotNull(row.title, row.artist, row.album, row.albumArtist, row.genre).joinToString(" ")
        }
        PROBES.forEach { probe ->
            val expression = LibraryText.ftsMatchExpression(probe)
            val memory = haystacks.count { LibraryText.containsAllTerms(it, probe) }
            val joined = timed {
                database.trackSearchDao().searchTrackIds(key, expression.orEmpty()).size.toString()
            }
            val plain = timed { plainMatch(key, expression.orEmpty()).toString() }
            lines += "probe#${PROBES.indexOf(probe)} expr=${expression != null} memory=$memory" +
                " fts=$joined plain=$plain"
        }
        return lines.joinToString("\n")
    }

    private fun sqliteVersion(): String = runCatching {
        database.openHelper.readableDatabase.query("SELECT sqlite_version()").use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else "?"
        }
    }.getOrElse { it.javaClass.simpleName }

    /** Тот же MATCH без JOIN — чтобы отличить ошибку запроса от ошибки индекса. */
    private fun plainMatch(key: String, expression: String): Int =
        database.openHelper.readableDatabase.query(
            "SELECT trackId FROM track_search WHERE track_search MATCH ? AND sourceKey = ?",
            arrayOf<Any?>(expression, key),
        ).use { it.count }

    private inline fun timed(block: () -> String): String {
        val started = System.nanoTime()
        val value = runCatching(block).getOrElse { "ERR:" + it.javaClass.simpleName }
        return "$value/${(System.nanoTime() - started) / NANOS_PER_MS}ms"
    }

    private companion object {
        /** Короткие общие пробы: латиница и кириллица, без личных данных. */
        val PROBES = listOf("a", "e", "ma", "а", "е", "ма")
        const val NANOS_PER_MS = 1_000_000L
    }
}
