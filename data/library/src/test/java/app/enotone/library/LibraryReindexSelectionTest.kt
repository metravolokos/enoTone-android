package app.enotone.library

import app.enotone.database.RemoteTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryReindexSelectionTest {
    private val source = LibrarySnapshotPlanner.accountKey("test@example.invalid")
    private val other = LibrarySnapshotPlanner.accountKey("other@example.invalid")

    private fun row(
        n: Int,
        title: String = "Track",
        album: String? = "Collaboration",
        artist: String? = "Singer",
        albumArtist: String? = "Ensemble",
        accountKey: String = source,
    ): RemoteTrackEntity {
        val path = "/AllMusic/${n}.flac"
        return RemoteTrackEntity(
            id = LibrarySnapshotPlanner.trackId(accountKey, path),
            sourceKey = accountKey, encodedPath = path, fileName = "${n}.flac",
            remoteEtag = "v1", remoteSize = 100000L, remoteModified = null,
            title = title, album = album, artist = artist, albumArtist = albumArtist,
            lastSeenEpoch = 1L, tagScanCode = "FLAC_VORBIS",
        )
    }

    @Test fun artistAndAlbumArtistAreNeverInterchanged() {
        val first = row(1, artist = "Guest", albumArtist = "Ensemble")
        val second = row(2, artist = "Soloist", albumArtist = "Ensemble")
        val third = row(3, artist = "Guest", albumArtist = "Other Ensemble")
        val rows = listOf(first, second, third)
        val tracks = OfflineTrackCatalog.build(source, rows)
        val artist = OfflineLibraryGrouping.artists(tracks).single { it.name == "Guest" }
        val albumArtist = OfflineLibraryGrouping.albumArtists(tracks).single {
            it.name == "Ensemble"
        }
        assertEquals(setOf(first.id, third.id),
            LibraryReindexSelection.select(source, rows,
                LibraryReindexScope.Artist(artist.key)).map { it.id }.toSet())
        assertEquals(setOf(first.id, second.id),
            LibraryReindexSelection.select(source, rows,
                LibraryReindexScope.AlbumArtist(albumArtist.key)).map { it.id }.toSet())
    }

    @Test fun albumScopeUsesAlbumArtistIdentityNotJustAlbumTitle() {
        val ensemble = row(1)
        val solo = row(2, albumArtist = "Soloist")
        val rows = listOf(ensemble, solo)
        val key = OfflineLibraryGrouping.albums(OfflineTrackCatalog.build(source, rows))
            .single { it.subtitle == "Ensemble" }.key
        assertEquals(listOf(ensemble.id), LibraryReindexSelection.select(
            source, rows, LibraryReindexScope.Album(key)).map { it.id })
    }

    @Test fun unknownAlbumIsSupportedButUnindexedRowsAreNotInvented() {
        val realUnknown = row(1, title = "Orphan", album = null)
        val unindexed = row(2, title = "", album = null,
            artist = null, albumArtist = null)
            .copy(title = null, tagScanCode = null)
        val rows = listOf(realUnknown, unindexed)
        val selected = LibraryReindexSelection.select(
            source, rows, LibraryReindexScope.Album("album:missing"))
        assertEquals(listOf(realUnknown.id), selected.map { it.id })
    }

    @Test fun wholeLibraryIsLocalAndIncludesOnlyActiveSupportedPlayableFiles() {
        val active = row(1)
        val another = row(2).copy(fileName = "2.mp3")
        val missing = row(3).copy(missingScans = 1)
        val empty = row(4).copy(remoteSize = 0L)
        val wrongFormat = row(5).copy(fileName = "5.ogg")
        val foreign = row(6, accountKey = other)
        val rows = listOf(active, another, missing, empty, wrongFormat, foreign)
        assertEquals(setOf(active.id, another.id),
            LibraryReindexSelection.select(source, rows,
                LibraryReindexScope.EntireLibrary).map { it.id }.toSet())
    }

    @Test fun largeArtistSelectionIsNotCappedAtTenOrSqlParameterLimit() {
        val rows = (1..3590).map { row(it) }
        val key = OfflineLibraryGrouping.artists(OfflineTrackCatalog.build(source, rows))
            .single().key
        val selection = LibraryReindexSelection.select(
            source, rows, LibraryReindexScope.Artist(key))
        assertEquals(3590, selection.size)
        assertTrue(selection.map { it.id }.distinct().size == 3590)
        assertEquals(400, LibraryReindexCoordinator.MAX_SQL_IDS_PER_BATCH)
        assertEquals(9, selection.take(3500).chunked(400).size)
    }

    @Test fun missingOrMaliciousGroupKeyDoesNotQueueAnotherGroup() {
        val rows = listOf(row(1))
        assertTrue(LibraryReindexSelection.select(source, rows,
            LibraryReindexScope.Artist("artist:unknown")).isEmpty())
        try {
            LibraryReindexSelection.select(source, rows,
                LibraryReindexScope.Artist("album:missing"))
            throw AssertionError("Expected invalid scope")
        } catch (_: IllegalArgumentException) {
            assertFalse(rows.isEmpty())
        }
    }
}
