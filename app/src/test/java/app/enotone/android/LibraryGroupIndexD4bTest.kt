package app.enotone.android

import app.enotone.library.OfflineLibraryGrouping
import app.enotone.library.OfflineTrackRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryGroupIndexD4bTest {
    private fun song(id: String, artist: String, album: String, author: String?) =
        OfflineTrackRow(id, id, artist, album, albumArtist = author, hasStoredTagData = true)

    @Test fun indexHasSameOwnAlbumsAsArtistDetailPlanner() {
        val rows = listOf(
            song("a", "Артист", "Сольный", "Артист"),
            song("b", "Артист", "Сольный", "Артист"),
            song("c", "Артист", "Сборник", "Various Artists"),
        )
        val index = LibraryGroupIndex.build(rows, "Неизвестный альбом")
        val artist = index.artists.single()
        val detail = ArtistDetailPlanner.plan(artist, index)
        assertEquals(1, detail.albums.size)
        assertEquals(3, detail.songs.size)
        assertFalse(detail.compilationsOnly)
    }

    @Test fun authorsCountUniqueAlbumsAndCompilationsOnlyOnce() {
        val rows = listOf(
            song("a", "Первый", "Общий", null),
            song("b", "Второй", "Общий", null),
            song("c", "Солист", "Один", "Солист"),
            song("d", "Солист", "Один", "Солист"),
            song("e", "Гость", "Другой", "VA"),
        )
        val index = LibraryGroupIndex.build(rows, "Неизвестный альбом", "Разные исполнители")
        val compilation = index.albumArtists.first()
        assertEquals(OfflineLibraryGrouping.COMPILATIONS_KEY, compilation.key)
        assertEquals(setOf("a", "b", "e"), compilation.trackIds)
        assertEquals(2, index.albumArtistAlbumCounts[compilation.key])
        val solo = index.albumArtists.single { it.name == "Солист" }
        assertEquals(1, index.albumArtistAlbumCounts[solo.key])
    }
}
