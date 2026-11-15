package app.enotone.android

import app.enotone.library.OfflineTrackRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumDurationTest {
    private fun song(id: String, duration: Long?) =
        OfflineTrackRow(id = id, title = id, artist = "Артист", album = "Альбом", durationMs = duration)

    @Test fun formatsKnownTrackDurationAsMinutesAndTwoDigitSeconds() {
        assertEquals("0:04", AlbumDuration.trackTime(4_999L))
        assertEquals("3:05", AlbumDuration.trackTime(185_500L))
        assertEquals("61:00", AlbumDuration.trackTime(3_660_000L))
    }

    @Test fun missingOrNonPositiveDurationNeverInventsTime() {
        assertNull(AlbumDuration.trackTime(null))
        assertNull(AlbumDuration.trackTime(0L))
        assertNull(AlbumDuration.trackTime(-1L))
        assertNull(AlbumDuration.totalMinutes(emptyList()))
        assertNull(AlbumDuration.totalMinutes(listOf(song("a", 120_000L), song("b", null))))
        assertNull(AlbumDuration.totalMinutes(listOf(song("a", 120_000L), song("b", 0L))))
    }

    @Test fun fullAlbumDurationIsSumOfAllKnownTracks() {
        assertEquals(6L, AlbumDuration.totalMinutes(listOf(
            song("a", 120_000L), song("b", 240_000L),
        )))
    }

    @Test fun albumFactsOmitOnlyUnknownPartsAndKeepOrder() {
        assertEquals("2024 · Рок · 2 песни, 6 мин", AlbumDuration.albumFacts(
            2024, "Рок", "2 песни", "6 мин",
        ))
        assertEquals("2 песни", AlbumDuration.albumFacts(null, null, "2 песни", null))
        assertEquals("2024 · 2 песни", AlbumDuration.albumFacts(2024, "", "2 песни", null))
        assertEquals("Рок · 2 песни", AlbumDuration.albumFacts(null, " Рок ", "2 песни", null))
    }
}
