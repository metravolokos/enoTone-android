package app.enotone.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTagFieldsTest {
    @Test fun normalizesCyrillicUnicodeAndPreservesEmbeddedTags() {
        val tags = AudioTagFields.fromRaw(
            title = "  Е\u0308ж  ",
            artist = " Исполнитель ",
            album = " Альбом ",
            albumArtist = "  Various Artists  ",
            fallbackFileName = "file.flac",
        )
        assertEquals("Ёж", tags.title)
        assertEquals("Исполнитель", tags.artist)
        assertEquals("Альбом", tags.album)
        assertEquals("Various Artists", tags.albumArtist)
    }

    @Test fun usesFileNameOnlyForMissingTitleNotFakeAlbumsOrArtists() {
        val tags = AudioTagFields.fromRaw(
            title = "  ", artist = null, album = "", albumArtist = null,
            fallbackFileName = "Track_001.FLAC",
        )
        assertEquals("Track_001", tags.title)
        assertNull(tags.artist)
        assertNull(tags.album)
        assertNull(tags.albumArtist)
    }

    @Test fun stripsControlCharactersAndCapsUntrustedTagLength() {
        val tags = AudioTagFields.fromRaw(
            title = "\t Secret\nSong \u0000", artist = "x".repeat(20_000),
            album = null, albumArtist = null, fallbackFileName = "fallback.mp3",
        )
        assertEquals("Secret Song", tags.title)
        assertEquals(512, tags.artist?.length)
    }

    @Test fun respectsDisplayTitleEvenWhenFileNameDiffers() {
        val tags = AudioTagFields.fromRaw(
            title = "Embedded title", artist = "A", album = null, albumArtist = null,
            fallbackFileName = "mismatching.wav",
        )
        assertEquals("Embedded title", tags.title)
    }

    @Test fun unknownTagsStayNullWithoutInventingMetadata() {
        val tags = AudioTagFields.fromRaw(
            title = null, artist = null, album = null, albumArtist = null,
            fallbackFileName = "",
        )
        assertNull(tags.title)
        assertNull(tags.artist)
        assertNull(tags.album)
        assertNull(tags.albumArtist)
    }
}
