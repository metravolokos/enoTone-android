package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueMetadataTest {
    @Test fun artistAndAlbumReachMediaMetadataBlankIsOmitted() {
        val track = QueueTrack("a".repeat(64), "Песня", "/AllMusic/a.mp3", 1L, null,
            artist = "Исполнитель", album = "Альбом")
        val metadata = QueueMetadata.of(track)
        assertEquals("Песня", metadata.title.toString())
        assertEquals("Исполнитель", metadata.artist.toString())
        assertEquals("Альбом", metadata.albumTitle.toString())
        assertNull(QueueMetadata.of(track.copy(artist = " ", album = null)).artist)
    }
}
