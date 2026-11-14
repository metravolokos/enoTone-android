package app.enotone.library

import app.enotone.database.RemoteTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineTrackCatalogTest {
    private val key = LibrarySnapshotPlanner.accountKey("synthetic@example.test")
    private val other = LibrarySnapshotPlanner.accountKey("other@example.test")

    private fun track(
        id: Int, name: String, title: String? = null, artist: String? = null,
        album: String? = null, source: String = key, missing: Int = 0,
    ): RemoteTrackEntity = RemoteTrackEntity(
        id = "id-${id}", sourceKey = source,
        encodedPath = "/AllMusic/test_${id}.flac", fileName = name,
        remoteEtag = null, remoteSize = 123, remoteModified = null,
        title = title, artist = artist, album = album, albumArtist = null,
        lastSeenEpoch = 1, missingScans = missing,
    )

    @Test fun preservesFullLocalLibraryWithFilenameFallbackAndNoRemoteSecrets() {
        val rows = listOf(
            track(1, "fallback.track.flac"),
            track(2, "hidden.flac", source = other),
            track(3, "gone.flac", missing = 1),
            track(4, "tagged.mp3", title = "Actual title", artist = "Artist", album = "Album"),
        )
        val presented = OfflineTrackCatalog.build(key, rows)
        assertEquals(2, presented.size)
        assertEquals(listOf("Actual title", "fallback.track"), presented.map { it.title })
        assertEquals("Artist", presented.first().artist)
        assertEquals("Album", presented.first().album)
        assertFalse(presented.any { it.title.contains("/AllMusic") })
        assertTrue(presented.any { it.artist == null })
        assertEquals(1, OfflineTrackCatalog.search(presented, "artist album").size)
        assertEquals(1, OfflineTrackCatalog.search(presented, "fallback").size)
        assertTrue(OfflineTrackCatalog.search(presented, "no match").isEmpty())
    }

    @Test fun genreIsAvailableToLocalSearchAndUnambiguousAlbumHeader() {
        val rows = listOf(
            OfflineTrackRow("id1", "First", "Performer", "Album", genre = "Rock"),
            OfflineTrackRow("id2", "Second", "Performer", "Album", genre = "rock"),
        )
        assertEquals(2, OfflineTrackCatalog.search(rows, "rock").size)
        assertEquals("Rock", OfflineTrackCatalog.commonAlbumGenre(rows))
        assertEquals(null, OfflineTrackCatalog.commonAlbumGenre(
            rows + OfflineTrackRow("id3", "Third", "Performer", "Album",
                genre = "Jazz")))
    }

    @Test fun durationFlowsIntoTheLocalTrackModelWithoutChangingItsUnknownValue() {
        val rows = OfflineTrackCatalog.build(key, listOf(
            track(1, "known.mp3").copy(durationMs = 187_000L),
            track(2, "unknown.mp3"),
        )).associateBy { it.id }
        assertEquals(187_000L, rows.getValue("id-1").durationMs)
        assertEquals(null, rows.getValue("id-2").durationMs)
    }

    @Test fun albumOrderUsesDiscAndTrackWhenAvailableWithStableFallback() {
        val rows = listOf(
            OfflineTrackRow("d2t2", "Coda", "Artist", "Album", year = 2024,
                trackNumber = 2, discNumber = 2),
            OfflineTrackRow("d1t3", "Prelude", "Artist", "Album", year = 2024,
                trackNumber = 3, discNumber = 1),
            OfflineTrackRow("d1t1", "Overture", "Artist", "Album", year = 2024,
                trackNumber = 1, discNumber = 1),
            OfflineTrackRow("untagged", "Late", "Artist", "Album"),
        )
        val sorted = OfflineTrackCatalog.albumOrder(rows)
        assertEquals(listOf("d1t1", "d1t3", "untagged", "d2t2"),
            sorted.map { it.id })
        assertEquals(2024, OfflineTrackCatalog.commonAlbumYear(rows))
        assertEquals(null, OfflineTrackCatalog.commonAlbumYear(
            rows + OfflineTrackRow("other", "Other", "Artist", "Album", year = 2022)))
    }

    @Test fun buildsAndSearches3590TracksWithoutTagsOrNetwork() {
        val tracks = (0 until 3590).map { i ->
            track(i, "track_${i.toString().padStart(4, '0')}.mp3")
        }
        val presented = OfflineTrackCatalog.build(key, tracks)
        assertEquals(3590, presented.size)
        assertEquals(1, OfflineTrackCatalog.search(presented, "track_3589").size)
        assertEquals(3590, OfflineTrackCatalog.search(presented, "").size)
        assertEquals(0, OfflineTrackCatalog.search(presented, "not-there").size)
    }
}
