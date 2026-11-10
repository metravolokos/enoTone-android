package app.enotone.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPageVisibilityTest {
    @Test fun allFourPagesVisibleByDefault() {
        assertEquals(listOf(
            LibraryPage.TRACKS,
            LibraryPage.ALBUMS,
            LibraryPage.ARTISTS,
            LibraryPage.ALBUM_ARTISTS,
        ), LibraryPageVisibility.visible(emptySet()))
    }

    @Test fun futureSettingsCanHideEitherArtistPageIndependently() {
        val hidden = setOf(LibraryPage.ALBUM_ARTISTS)
        assertEquals(listOf(
            LibraryPage.TRACKS, LibraryPage.ALBUMS, LibraryPage.ARTISTS,
        ), LibraryPageVisibility.visible(hidden))
        assertTrue(LibraryPage.ALBUM_ARTISTS !in LibraryPageVisibility.visible(hidden))
        assertTrue(LibraryPage.ARTISTS in LibraryPageVisibility.visible(hidden))
    }

    @Test fun hidingAllPagesLeavesTracksRecoverable() {
        assertEquals(listOf(LibraryPage.TRACKS),
            LibraryPageVisibility.visible(LibraryPage.entries.toSet()))
    }

    @Test fun hiddenSelectedTabFallsBackWithoutChangingTracks() {
        val pages = LibraryPageVisibility.visible(setOf(LibraryPage.ARTISTS))
        assertEquals(LibraryPage.TRACKS,
            LibraryPageVisibility.selectedOrFallback(LibraryPage.ARTISTS, pages))
        assertEquals(LibraryPage.ALBUMS,
            LibraryPageVisibility.selectedOrFallback(LibraryPage.ALBUMS, pages))
    }
}
