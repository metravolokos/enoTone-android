package app.enotone.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumTagRefreshSelectionTest {
    @Test fun unfinishedAttemptCanBeRetriedOnlyAfterUntouchedAlbumTracks() {
        val nine = (1..9).map { "local-id-$it" }
        val selected = AlbumTagRefreshSelection.nextBatch(
            albumIds = nine,
            completedIds = (1..6).map { "local-id-$it" }.toSet(),
            attemptedIds = (1..7).map { "local-id-$it" }.toSet(),
        )
        // Six committed reads, track 7 failed, tracks 8 & 9 unattempted.
        assertEquals(listOf("local-id-8", "local-id-9", "local-id-7"), selected)
    }

    @Test fun alreadyCompletedRecordsAreNeverRequeuedWithinScreenSession() {
        val names = (1..3590).map { "track-$it" }
        val completed = names.take(20).toSet()
        val selected = AlbumTagRefreshSelection.nextBatch(
            names, completed, names.take(21).toSet(),
        )
        assertEquals(10, selected.size)
        assertEquals(names.drop(21).take(10), selected)
        assertTrue(selected.none { it in completed })
    }

    @Test fun duplicateLocalIdsAndShortAlbumsRemainBounded() {
        assertEquals(
            listOf("a", "b"),
            AlbumTagRefreshSelection.nextBatch(listOf("a", "a", "b"), emptySet(), emptySet()),
        )
        assertTrue(AlbumTagRefreshSelection.nextBatch(
            listOf("a"), setOf("a"), setOf("a")
        ).isEmpty())
    }
}
