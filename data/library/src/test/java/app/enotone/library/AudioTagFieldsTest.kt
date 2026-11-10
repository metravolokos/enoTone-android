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

    @Test fun releaseDateAndOrdinalsAreParsedWithinSafeBounds() {
        val tags = AudioTagFields.fromRaw(
            title = "Title", artist = null, album = "Album", albumArtist = null,
            fallbackFileName = "track.flac",
            rawYear = "2024-06-12", rawTrackNumber = "03/15", rawDiscNumber = "2/3",
        )
        assertEquals(2024, tags.year)
        assertEquals(3, tags.trackNumber)
        assertEquals(2, tags.discNumber)
    }

    @Test fun unknownOrMalformedNumericTagsNeverFabricateMetadata() {
        for ((year, track, disc) in listOf(
            Triple("2024/2025", "0", "0"),
            Triple("9999", "1000/1001", "-2"),
            Triple("something", "abc/5", "1x"),
            Triple("2020\u0000", "1/unsafe", "x".repeat(50)),
        )) {
            val tags = AudioTagFields.fromRaw(
                title = null, artist = null, album = null, albumArtist = null,
                fallbackFileName = "unknown.flac",
                rawYear = year, rawTrackNumber = track, rawDiscNumber = disc,
            )
            assertNull(tags.year)
            assertNull(tags.trackNumber)
            assertNull(tags.discNumber)
        }
    }

    @Test fun id3GenreCodesAndFreeTextAreNormalizedWithoutFabrication() {
        val base = AudioTagFields.fromRaw(null, null, null, null, "album.flac",
            rawGenre = "(17)")
        assertEquals("Rock", base.genre)
        assertEquals("Rock", Id3Genres.normalize("17"))
        assertEquals("Русский рок", Id3Genres.normalize(" Русский рок "))
        assertNull(Id3Genres.normalize("(999)"))
        assertNull(Id3Genres.normalize("255"))
        assertEquals("Рок", Id3Genres.normalize("Рок"))
        assertEquals(512, Id3Genres.normalize("x".repeat(2000))?.length)
        assertNull(Id3Genres.normalize(" \u0000 "))
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
