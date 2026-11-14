package app.enotone.android

import app.enotone.library.OfflineTrackRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackActionPlannerTest {
    private val albumTrack = OfflineTrackRow(
        id = "one", title = "Песня", artist = "Артист", album = "Альбом",
        albumArtist = "Автор", hasStoredTagData = true,
    )
    private val orphan = OfflineTrackRow(
        id = "two", title = "Без группы", artist = null, album = null,
        hasStoredTagData = true,
    )

    @Test fun targetsMatchLibraryGroupingRatherThanAlbumArtist() {
        val targets = TrackActionPlanner.targets(albumTrack,
            LibraryGroupIndex.build(listOf(albumTrack), "Неизвестный альбом"), null)
        assertEquals("artist:артист", targets.artist?.key)
        assertEquals("album:альбом\u0000автор", targets.album?.key)
    }

    @Test fun existingDetailIsHiddenButOtherNavigationRemains() {
        val album = TrackActionPlanner.targets(albumTrack,
            LibraryGroupIndex.build(listOf(albumTrack), "Неизвестный альбом"),
            LibraryDetail.Album("album:альбом\u0000автор"))
        assertNull(album.album)
        assertEquals("artist:артист", album.artist?.key)
        val artist = TrackActionPlanner.targets(albumTrack,
            LibraryGroupIndex.build(listOf(albumTrack), "Неизвестный альбом"),
            LibraryDetail.Artist("artist:артист"))
        assertNull(artist.artist)
        assertEquals("Альбом", artist.album?.name)
    }

    @Test fun missingTagsDoNotInventTargets() {
        val targets = TrackActionPlanner.targets(orphan,
            LibraryGroupIndex.build(listOf(orphan), "Неизвестный альбом"), null)
        assertNull(targets.album)
        assertNull(targets.artist)
    }
}
