package app.enotone.library

import app.enotone.database.RemoteTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedAlbumTagRefreshPolicyTest {
    private val account = LibrarySnapshotPlanner.accountKey("synthetic@example.test")

    private fun row(number: Int): RemoteTrackEntity {
        val path = "/AllMusic/track_${number}.flac"
        return RemoteTrackEntity(
            id = LibrarySnapshotPlanner.trackId(account, path),
            sourceKey = account, encodedPath = path, fileName = "track_${number}.flac",
            remoteEtag = "v1", remoteSize = 100_000L, remoteModified = null,
            title = "Title", artist = "Artist", album = "Album",
            albumArtist = "Album Artist",
            lastSeenEpoch = 1L, tagScanCode = "RETRY_REFRESH",
        )
    }

    @Test fun onlyChosenTenIdsCanEnterPilotSelection() {
        val all = (0 until 3590).map(::row)
        val selected = all.drop(200).take(10).map { it.id }.toSet()
        val scoped = PilotBudget.select(account, all.filter { it.id in selected }, 10)
        assertEquals(10, scoped.size)
        assertEquals(selected, scoped.map { it.id }.toSet())
        assertTrue(scoped.all { it.sourceKey == account })
        assertEquals(10, AlbumTagRefresher.MAX_FILES_PER_TAP)
    }

    @Test fun alreadyTaggedFilesNeedExplicitRetryFlag() {
        val row = row(1).copy(tagScanCode = "FLAC_VORBIS")
        val ordinary = PilotBudget.select(account, listOf(row), 10)
        assertTrue(ordinary.isEmpty())
        val manuallyQueued = PilotBudget.select(account,
            listOf(row.copy(tagScanCode = "RETRY_REFRESH")), 10)
        assertEquals(listOf(row.id), manuallyQueued.map { it.id })
    }

    @Test fun unrelatedAccountCannotEnterSelection() {
        val wrong = row(2).copy(sourceKey = "another-account")
        val result = PilotBudget.select(account, listOf(wrong), 10)
        assertTrue(result.isEmpty())
    }
}
