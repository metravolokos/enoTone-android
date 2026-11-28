package app.enotone.android

import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineTrackRow
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentlyAddedPlannerTest {
    @Test fun songsSortByDateThenIdExcludeUnknownDatesAndRespectBothLimits() {
        val rows = buildList {
            add(row("z", "Z", 50L))
            add(row("b", "B", 100L))
            add(row("a", "A", 100L))
            add(row("old", "Old", 1L))
            add(row("unknown", "Unknown"))
            repeat(110) { index ->
                add(row("bulk-$index", "Bulk $index", 40L - index))
            }
        }

        assertEquals(listOf("a", "b", "z", "bulk-0", "bulk-1"),
            RecentlyAddedPlanner.tracks(rows, RecentlyAddedPlanner.HOME_TRACKS_LIMIT)
                .map { it.id })
        assertEquals(100, RecentlyAddedPlanner.tracks(rows).size)
        assertEquals(0, RecentlyAddedPlanner.tracks(rows, 0).size)
    }

    @Test fun albumDateIsNewestMemberAndUnknownOrUndatedGroupsAreExcluded() {
        val rows = listOf(
            row("old", "Old", 10L),
            row("new", "New", 90L),
            row("no-date", "No date"),
        ).associateBy { it.id }
        val groups = listOf(
            OfflineLibraryGroup("album:old", "Old release", null, setOf("old")),
            OfflineLibraryGroup("album:new", "Recently updated release", null, setOf("old", "new")),
            OfflineLibraryGroup("album:missing", "Неизвестный альбом", null, setOf("new")),
            OfflineLibraryGroup("album:undated", "Undated", null, setOf("no-date")),
        )

        assertEquals(listOf("album:new", "album:old"),
            RecentlyAddedPlanner.albums(groups, rows).map { it.key })
    }

    @Test fun albumsStopAtFiftyAndHomeShowsAtMostTen() {
        val rows = (0..54).associate { index ->
            "track-$index" to row("track-$index", "Track $index", index.toLong())
        }
        val groups = rows.keys.mapIndexed { index, id ->
            OfflineLibraryGroup("album:$index", "Album $index", null, setOf(id))
        }

        assertEquals(10,
            RecentlyAddedPlanner.albums(groups, rows, RecentlyAddedPlanner.HOME_ALBUMS_LIMIT).size)
        assertEquals(50, RecentlyAddedPlanner.albums(groups, rows).size)
    }

    /** Первый импорт аккаунта: `firstSeenEpochMs` нет ни у кого — порядок задаёт дата файла. */
    @Test fun serverFileDateOrdersFirstImportAndOnlyDoubleUndatedRowsDropOut() {
        val rows = listOf(
            row("mid", "Mid", firstSeen = null, serverDate = 500L),
            row("newest", "Newest", firstSeen = null, serverDate = 900L),
            row("no-date", "No date"),
            row("account-date-wins", "Wins", firstSeen = 700L, serverDate = 1L),
        )

        assertEquals(listOf("newest", "account-date-wins", "mid"),
            RecentlyAddedPlanner.tracks(rows, RecentlyAddedPlanner.HOME_TRACKS_LIMIT)
                .map { it.id })
    }

    /** Дата аккаунта имеет приоритет; дата файла — только запасной вариант. */
    @Test fun albumDateUsesFileDateWhenTrackingDateIsAbsent() {
        val rows = listOf(
            row("a", "A", firstSeen = null, serverDate = 10L),
            row("b", "B", firstSeen = null, serverDate = 90L),
            row("c", "C", firstSeen = null),
        ).associateBy { it.id }
        val groups = listOf(
            OfflineLibraryGroup("album:a", "A", null, setOf("a")),
            OfflineLibraryGroup("album:b", "B", null, setOf("b", "c")),
            OfflineLibraryGroup("album:undated", "Undated", null, setOf("c")),
        )

        assertEquals(listOf("album:b", "album:a"),
            RecentlyAddedPlanner.albums(groups, rows).map { it.key })
    }

    private fun row(
        id: String,
        title: String,
        firstSeen: Long? = null,
        serverDate: Long? = null,
    ) = OfflineTrackRow(
        id = id,
        title = title,
        artist = "",
        album = "",
        firstSeenEpochMs = firstSeen,
        sourceModifiedEpochMs = serverDate,
    )
}
