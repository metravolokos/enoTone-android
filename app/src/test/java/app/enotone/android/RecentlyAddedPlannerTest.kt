package app.enotone.android

import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineTrackRow
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentlyAddedPlannerTest {
    @Test fun songsSortByDateThenIdExcludeUnknownDatesAndRespectBothLimits() {
        val rows = buildList {
            add(OfflineTrackRow("z", "Z", firstSeenEpochMs = 50L))
            add(OfflineTrackRow("b", "B", firstSeenEpochMs = 100L))
            add(OfflineTrackRow("a", "A", firstSeenEpochMs = 100L))
            add(OfflineTrackRow("old", "Old", firstSeenEpochMs = 1L))
            add(OfflineTrackRow("unknown", "Unknown"))
            repeat(110) { index ->
                add(OfflineTrackRow("bulk-$index", "Bulk $index", firstSeenEpochMs = 40L - index))
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
            OfflineTrackRow("old", "Old", firstSeenEpochMs = 10L),
            OfflineTrackRow("new", "New", firstSeenEpochMs = 90L),
            OfflineTrackRow("no-date", "No date"),
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
            "track-$index" to OfflineTrackRow(
                id = "track-$index",
                title = "Track $index",
                firstSeenEpochMs = index.toLong(),
            )
        }
        val groups = rows.keys.mapIndexed { index, id ->
            OfflineLibraryGroup("album:$index", "Album $index", null, setOf(id))
        }

        assertEquals(10,
            RecentlyAddedPlanner.albums(groups, rows, RecentlyAddedPlanner.HOME_ALBUMS_LIMIT).size)
        assertEquals(50, RecentlyAddedPlanner.albums(groups, rows).size)
    }
}
