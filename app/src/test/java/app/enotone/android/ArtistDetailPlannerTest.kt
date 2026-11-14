package app.enotone.android

import app.enotone.library.OfflineLibraryGrouping
import app.enotone.library.OfflineTrackRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistDetailPlannerTest {
    private fun song(
        id: String, album: String? = null, albumArtist: String? = null,
        year: Int? = null, number: Int? = null,
    ) = OfflineTrackRow(id, id, "Певец", album, albumArtist,
        year = year, trackNumber = number, hasStoredTagData = true)

    @Test fun ownAlbumsOrderedNewestFirstAndSongsByDiscAndTrack() {
        val tracks = listOf(
            song("old2", "Ранний", "Певец", 2001, 2),
            song("new2", "Новый", "Певец", 2024, 2),
            song("missing", null),
            song("old1", "Ранний", "Певец", 2001, 1),
            song("new1", "Новый", "Певец", 2024, 1),
        )
        val artist = OfflineLibraryGrouping.artists(tracks).single()
        val plan = ArtistDetailPlanner.plan(artist, tracks, "Неизвестный альбом")
        assertEquals(listOf("Новый", "Ранний"), plan.albums.map { it.group.name })
        assertEquals(listOf("new1", "new2", "old1", "old2", "missing"), plan.songs.map { it.id })
        assertFalse(plan.compilationsOnly)
    }

    @Test fun compilationDoesNotBecomeArtistAlbum() {
        val tracks = listOf(song("one", "Песни в сборниках", "Разные исполнители", 2020))
        val artist = OfflineLibraryGrouping.artists(tracks).single()
        val plan = ArtistDetailPlanner.plan(artist, tracks, "Неизвестный альбом")
        assertTrue(plan.compilationsOnly)
        assertTrue(plan.albums.isEmpty())
        assertEquals(listOf("one"), plan.songs.map { it.id })
    }

    @Test fun albumsWithUnknownYearComeLast() {
        val tracks = listOf(song("a", "Без года", "Певец"),
            song("b", "Известный", "Певец", 2015))
        val artist = OfflineLibraryGrouping.artists(tracks).single()
        assertEquals(listOf("Известный", "Без года"),
            ArtistDetailPlanner.plan(artist, tracks, "Неизвестный альбом").albums.map { it.group.name })
    }
}
