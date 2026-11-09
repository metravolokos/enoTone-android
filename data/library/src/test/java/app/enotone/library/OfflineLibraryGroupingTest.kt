package app.enotone.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineLibraryGroupingTest {
    private fun row(
        id: String, album: String? = null, artist: String? = null,
        albumArtist: String? = null,
    ) = OfflineTrackRow(id, "Synthetic $id", artist, album, albumArtist)

    @Test fun unscannedRowsNeverBecomeFakeUnknownAlbumOrArtist() {
        val rows = (0 until 3590).map { row("synthetic-$it") }
        assertTrue(OfflineLibraryGrouping.albums(rows).isEmpty())
        assertTrue(OfflineLibraryGrouping.artists(rows).isEmpty())
    }

    @Test fun recognizedTagsFormStableGroupsWithoutChangingTracks() {
        val rows = (0 until 3560).map { row("untagged-$it") } + listOf(
            row("a", "First Album", "Singer", "Various Artists"),
            row("b", "First Album", "Guest", "Various Artists"),
            row("c", "Other Album", "Singer"),
        )
        val albums = OfflineLibraryGrouping.albums(rows)
        val artists = OfflineLibraryGrouping.artists(rows)
        assertEquals(2, albums.size)
        assertEquals(setOf("a", "b"), albums.first { it.name == "First Album" }.trackIds)
        assertEquals(2, artists.size)
        assertEquals(setOf("a", "c"), artists.first { it.name == "Singer" }.trackIds)
        assertEquals(3563, rows.size)
    }

    @Test fun sameAlbumNameDifferentArtistsNotAccidentallyMerged() {
        val albums = OfflineLibraryGrouping.albums(listOf(
            row("one", "Greatest Hits", "One"),
            row("two", "Greatest Hits", "Two"),
        ))
        assertEquals(2, albums.size)
        assertTrue(albums.all { it.trackIds.size == 1 })
    }

    @Test fun unicodeCaseAndWhitespaceGroupConsistentlyAndSearchIsLocal() {
        val album = OfflineLibraryGrouping.albums(listOf(
            row("a", "Cafe\u0301", "ARTIST"),
            row("b", "Café ", "artist"),
        ))
        assertEquals(1, album.size)
        assertEquals(setOf("a", "b"), album.single().trackIds)
        assertTrue(OfflineLibraryGrouping.matches(album.single(), " CAFÉ artist "))
        assertFalse(OfflineLibraryGrouping.matches(album.single(), "Unrelated"))
    }

    @Test fun knownAlbumWithoutArtistRemainsVisibleWithoutInventingArtist() {
        val one = row("single", album = "Instrumental")
        val albums = OfflineLibraryGrouping.albums(listOf(one))
        assertEquals(1, albums.size)
        assertEquals(null, albums.single().subtitle)
        assertTrue(OfflineLibraryGrouping.artists(listOf(one)).isEmpty())
    }
}
