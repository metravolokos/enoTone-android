package app.enotone.library

import app.enotone.database.RemoteTrackEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTagStoreTest {
    private val source = LibrarySnapshotPlanner.accountKey("listener@example.test")
    private val path = "/AllMusic/song.flac"
    private val original = RemoteTrackEntity(
        id = LibrarySnapshotPlanner.trackId(source, path),
        sourceKey = source,
        encodedPath = path,
        fileName = "song.flac",
        remoteEtag = "synthetic-etag",
        remoteSize = 12345,
        remoteModified = "Sun, 08 Nov 2026 00:00:00 GMT",
        lastSeenEpoch = 1,
    )

    @Test fun onlyPresentMatchingFlatTrackCanBeWritten() {
        assertTrue(eligibleForSingleTagWrite(source, original))
        assertFalse(eligibleForSingleTagWrite(source, original.copy(missingScans = 1)))
        assertFalse(eligibleForSingleTagWrite(source, original.copy(remoteSize = null)))
        assertFalse(eligibleForSingleTagWrite(source, original.copy(remoteSize = 0)))
        assertFalse(eligibleForSingleTagWrite(source, original.copy(id = "wrong")))
        assertFalse(eligibleForSingleTagWrite(source, original.copy(sourceKey = "other")))
        assertFalse(eligibleForSingleTagWrite("other", original))
    }

    @Test fun cannotWriteNestedOrEmptyPath() {
        val nested = "/AllMusic/dir/song.flac"
        assertFalse(eligibleForSingleTagWrite(source, original.copy(
            id = LibrarySnapshotPlanner.trackId(source, nested),
            encodedPath = nested,
        )))
        assertFalse(eligibleForSingleTagWrite(source, original.copy(
            id = LibrarySnapshotPlanner.trackId(source, "/AllMusic/"),
            encodedPath = "/AllMusic/",
        )))
    }
}
