package app.enotone.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineLibraryGroupingTest {
    private fun row(
        id: String, album: String? = null, artist: String? = null,
        albumArtist: String? = null, hasStoredTags: Boolean = false,
    ) = OfflineTrackRow(id, "Synthetic $id", artist, album, albumArtist,
        hasStoredTagData = hasStoredTags)

    @Test fun unscannedRowsNeverBecomeFakeUnknownAlbumOrArtist() {
        val rows = (0 until 3590).map { row("synthetic-$it") }
        assertTrue(OfflineLibraryGrouping.albums(rows).isEmpty())
        assertTrue(OfflineLibraryGrouping.artists(rows).isEmpty())
        assertTrue(OfflineLibraryGrouping.albumArtists(rows).isEmpty())
    }

    @Test fun recognizedTagsFormStableGroupsWithoutChangingTracks() {
        val rows = (0 until 3560).map { row("untagged-$it") } + listOf(
            row("a", "First Album", "Singer", "Various Artists"),
            row("b", "First Album", "Guest", "Various Artists"),
            row("c", "Other Album", "Singer"),
        )
        val albums = OfflineLibraryGrouping.albums(rows)
        val artists = OfflineLibraryGrouping.artists(rows)
        val albumArtists = OfflineLibraryGrouping.albumArtists(rows)
        assertEquals(2, albums.size)
        assertEquals(setOf("a", "b"), albums.first { it.name == "First Album" }.trackIds)
        assertEquals(2, artists.size)
        assertEquals(setOf("a", "c"), artists.first { it.name == "Singer" }.trackIds)
        assertEquals(setOf("b"), artists.first { it.name == "Guest" }.trackIds)
        assertEquals(1, albumArtists.size)
        assertEquals(setOf("a", "b"), albumArtists.single().trackIds)
        assertEquals(3563, rows.size)
    }

    @Test fun artistsAndAlbumArtistsRemainDistinctWhenTagsDiffer() {
        val rows = listOf(
            row("one", "Duets", "Guest One", "Album Collective"),
            row("two", "Duets", "Guest Two", "Album Collective"),
            row("three", "Solo", "Guest One"),
        )
        val artists = OfflineLibraryGrouping.artists(rows)
        val albumArtists = OfflineLibraryGrouping.albumArtists(rows)
        assertEquals(setOf("one", "three"),
            artists.first { it.name == "Guest One" }.trackIds)
        assertEquals(setOf("two"),
            artists.first { it.name == "Guest Two" }.trackIds)
        assertEquals(setOf("one", "two"),
            albumArtists.single { it.name == "Album Collective" }.trackIds)
        assertFalse(albumArtists.any { it.name == "Guest One" })
        assertEquals(1, OfflineLibraryGrouping.albums(rows).count { it.name == "Duets" })
    }

    @Test fun missingAlbumWithOtherSavedTagsAppearsInUnknownAlbum() {
        val rows = listOf(
            row("without-album", artist = "Singer", hasStoredTags = true),
            row("another-without-album", artist = "Singer", hasStoredTags = true),
            row("with-album", album = "Recognized", artist = "Singer"),
            row("unscanned"),
        )
        val groups = OfflineLibraryGrouping.albums(rows, "Неизвестный альбом")
        assertEquals(2, groups.size)
        val unknown = groups.single { it.name == "Неизвестный альбом" }
        assertEquals("Singer", unknown.subtitle)
        assertEquals(setOf("without-album", "another-without-album"), unknown.trackIds)
        assertTrue(groups.last().key.startsWith("album:missing"))
        assertFalse(groups.any { "unscanned" in it.trackIds })
    }

    @Test fun literalUnknownAlbumAndMissingAlbumHaveDifferentKeys() {
        val rows = listOf(
            row("literal", album = "Неизвестный альбом", artist = "Singer"),
            row("missing", album = null, artist = "Singer"),
        )
        val groups = OfflineLibraryGrouping.albums(rows, "Неизвестный альбом")
        assertEquals(2, groups.size)
        assertEquals(2, groups.map { it.key }.toSet().size)
        assertTrue(groups.any { it.trackIds == setOf("literal") })
        assertTrue(groups.any { it.trackIds == setOf("missing") })
    }

    @Test fun titleOnlyStoredMetadataStillCreatesUnknownAlbum() {
        val one = row("title-only", hasStoredTags = true)
        val groups = OfflineLibraryGrouping.albums(listOf(one), "Unknown Album")
        assertEquals(1, groups.size)
        assertEquals(setOf("title-only"), groups.single().trackIds)
        assertEquals(null, groups.single().subtitle)
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

    @Test fun albumArtistOnlyAppearsInAlbumArtistPageNotTrackArtistPage() {
        val item = row("album-artist-only", album = "Album",
            albumArtist = "The Orchestra")
        assertTrue(OfflineLibraryGrouping.artists(listOf(item)).isEmpty())
        val groups = OfflineLibraryGrouping.albumArtists(listOf(item))
        assertEquals(1, groups.size)
        assertEquals("The Orchestra", groups.single().name)
        assertEquals(setOf("album-artist-only"), groups.single().trackIds)
    }

    @Test fun knownAlbumWithoutArtistRemainsVisibleWithoutInventingArtist() {
        val one = row("single", album = "Instrumental")
        val albums = OfflineLibraryGrouping.albums(listOf(one))
        assertEquals(1, albums.size)
        assertEquals(null, albums.single().subtitle)
        assertTrue(OfflineLibraryGrouping.artists(listOf(one)).isEmpty())
        assertTrue(OfflineLibraryGrouping.albumArtists(listOf(one)).isEmpty())
    }
}
