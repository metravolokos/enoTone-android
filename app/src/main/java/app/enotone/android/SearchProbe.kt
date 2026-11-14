package app.enotone.android

import app.enotone.database.EnoToneDatabase
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.LibraryText

/**
 * Проверка поиска на устройстве (BUG track search, 2026-11-14). MATCH FTS4 на Huawei зависал
 * и держал базу, поэтому здесь запросы FTS **не выполняются**: только план запроса
 * (EXPLAIN QUERY PLAN), версия SQLite, число строк и поиск в памяти. Наружу — числа и коды.
 */
internal class SearchProbe(private val database: EnoToneDatabase) {
    suspend fun run(email: String): String {
        val key = LibrarySnapshotPlanner.accountKey(email)
        val tracks = database.remoteTrackDao().allForSource(key).filter { it.missingScans == 0 }
        val indexRows = database.trackSearchDao().allForSource(key).size
        val haystacks = tracks.map { row ->
            listOfNotNull(row.title, row.artist, row.album, row.albumArtist, row.genre).joinToString(" ")
        }
        val lines = mutableListOf("sqlite=${sqliteVersion()} tracks=${tracks.size} index=$indexRows")
        PROBES.forEachIndexed { index, probe ->
            val started = System.nanoTime()
            val memory = haystacks.count { LibraryText.containsAllTerms(it, probe) }
            lines += "probe#$index memory=$memory ${(System.nanoTime() - started) / NANOS_PER_MS}ms"
        }
        lines += "plan-join: " + plan(JOINED, key)
        lines += "plan-plain: " + plan(PLAIN, key)
        return lines.joinToString("\n")
    }

    private fun sqliteVersion(): String = runCatching {
        database.openHelper.readableDatabase.query("SELECT sqlite_version()").use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else "?"
        }
    }.getOrElse { it.javaClass.simpleName }

    /** Только план: запрос не выполняется и не может занять базу. */
    private fun plan(sql: String, key: String): String = runCatching {
        database.openHelper.readableDatabase.query(
            "EXPLAIN QUERY PLAN $sql", arrayOf<Any?>(key, "\"ma*\""),
        ).use { cursor ->
            val detail = cursor.getColumnIndex("detail")
            buildList { while (cursor.moveToNext()) add(cursor.getString(detail)) }.joinToString(" / ")
        }
    }.getOrElse { "ERR:" + it.javaClass.simpleName }

    private companion object {
        /** Короткие общие пробы: латиница и кириллица, без личных данных. */
        val PROBES = listOf("a", "e", "ma", "а", "е", "ма")
        const val NANOS_PER_MS = 1_000_000L
        const val JOINED = "SELECT track_search.trackId FROM track_search JOIN remote_tracks " +
            "ON remote_tracks.id = track_search.trackId WHERE track_search.sourceKey = ?1 " +
            "AND remote_tracks.sourceKey = ?1 AND remote_tracks.missingScans = 0 " +
            "AND track_search MATCH ?2"
        const val PLAIN = "SELECT trackId FROM track_search WHERE sourceKey = ?1 AND track_search MATCH ?2"
    }
}
