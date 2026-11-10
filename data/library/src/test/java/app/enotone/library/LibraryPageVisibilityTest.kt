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

    @Test fun settingsHideAndRestorePagesIndependently() {
        var hidden = emptySet<LibraryPage>()
        hidden = LibraryPageVisibility.withVisibility(
            hidden, LibraryPage.ALBUM_ARTISTS, shouldShow = false)
        hidden = LibraryPageVisibility.withVisibility(
            hidden, LibraryPage.ARTISTS, shouldShow = false)
        assertEquals(listOf(LibraryPage.TRACKS, LibraryPage.ALBUMS),
            LibraryPageVisibility.visible(hidden))
        hidden = LibraryPageVisibility.withVisibility(
            hidden, LibraryPage.ARTISTS, shouldShow = true)
        assertEquals(listOf(
            LibraryPage.TRACKS, LibraryPage.ALBUMS, LibraryPage.ARTISTS),
            LibraryPageVisibility.visible(hidden))
        assertEquals(setOf(LibraryPage.ALBUM_ARTISTS), hidden)
    }

    @Test fun settingsWillNotHideTheLastRemainingPage() {
        var hidden = emptySet<LibraryPage>()
        for (page in listOf(
            LibraryPage.ALBUMS, LibraryPage.ARTISTS, LibraryPage.ALBUM_ARTISTS,
        )) hidden = LibraryPageVisibility.withVisibility(hidden, page, false)
        val attempt = LibraryPageVisibility.withVisibility(
            hidden, LibraryPage.TRACKS, shouldShow = false)
        assertEquals(hidden, attempt)
        assertEquals(listOf(LibraryPage.TRACKS), LibraryPageVisibility.visible(attempt))
    }

    @Test fun persistedPageIdsRestoreAcrossUiAndIgnoreUnknownValues() {
        val saved = setOf(LibraryPage.ARTISTS, LibraryPage.ALBUM_ARTISTS)
        val serialized = LibraryPageVisibility.persistedHidden(saved)
        assertEquals(setOf("ARTISTS", "ALBUM_ARTISTS"), serialized)
        val restored = LibraryPageVisibility.parseHidden(
            serialized + setOf("LEGACY", "UNKNOWN", "tracks"),
        )
        assertEquals(saved, restored)
        assertEquals(listOf(LibraryPage.TRACKS, LibraryPage.ALBUMS),
            LibraryPageVisibility.visible(restored))
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
