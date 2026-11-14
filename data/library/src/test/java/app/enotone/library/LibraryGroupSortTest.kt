package app.enotone.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryGroupSortTest {
    @Test fun russianOrderingDoesNotChangeAlbumOrArtworkIdentity() {
        val rows = listOf(
            OfflineTrackRow("one", "One", "Ёж", "Ежик", hasStoredTagData = true),
            OfflineTrackRow("two", "Two", "Йод", "Йод", hasStoredTagData = true),
            OfflineTrackRow("three", "Three", "Ива", "Ива", hasStoredTagData = true),
            OfflineTrackRow("missing", "Missing", "Ёж", null, hasStoredTagData = true),
        )
        val albums = OfflineLibraryGrouping.albums(rows, "Неизвестный альбом")
        assertEquals(setOf("album:ежик\u0000ёж", "album:йод\u0000йод",
            "album:ива\u0000ива", "album:missing"), albums.map { it.key }.toSet())
        assertEquals("album:missing", albums.last().key)
        assertEquals(setOf("one", "missing"),
            OfflineLibraryGrouping.artists(rows).single { it.key == "artist:ёж" }.trackIds)
    }

    @Test fun performerTheArticleDoesNotChangeGroupIdentity() {
        val groups = OfflineLibraryGrouping.artists(listOf(
            OfflineTrackRow("a", "A", "The Beatles", "Album"),
            OfflineTrackRow("b", "B", "Abba", "Album"),
            OfflineTrackRow("c", "C", "Zebra", "Album"),
        ))
        assertEquals(listOf("Abba", "The Beatles", "Zebra"), groups.map { it.name })
        assertTrue(groups.any { it.key == "artist:the beatles" })
    }
}
