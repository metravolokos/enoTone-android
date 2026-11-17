package app.enotone.library

import app.enotone.database.RemoteTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryDuplicateDiagnosticsTest {
    private fun row(id: String, path: String, album: String = "Fixture") = RemoteTrackEntity(
        id, "source", path, "fixture.mp3", null, 100L, null,
        title = "Fixture", artist = "Fixture", album = album, lastSeenEpoch = 1,
    )

    @Test fun countsPathAliasesAcrossAlbumsWithoutReturningPrivateData() {
        val rows = listOf(
            row("a", "/AllMusic/a.mp3"), row("b", "/AllMusic/%61.mp3"),
            row("c", "/AllMusic/%d0%b0.mp3", "Second"),
            row("d", "/AllMusic/%D0%B0.mp3", "Second"),
        )
        assertEquals(LibraryDuplicateAudit(2, 2), LibraryDuplicateDiagnostics.inspect("source", rows))
    }

    @Test fun sameTagsAreNotProofOfDuplicateFilesAndOtherAccountsAreExcluded() {
        val rows = listOf(row("a", "/AllMusic/a.mp3"), row("b", "/AllMusic/b.mp3"),
            row("c", "/AllMusic/a.mp3").copy(sourceKey = "other"),
            row("d", "/AllMusic/a.mp3").copy(missingScans = 1))
        assertEquals(LibraryDuplicateAudit(0, 0), LibraryDuplicateDiagnostics.inspect("source", rows))
    }

    @Test fun reservedCharactersCaseAndUnicodeNormalizationAreNotMerged() {
        val paths = listOf("/AllMusic/a+b.mp3", "/AllMusic/a%20b.mp3", "/AllMusic/a%2Bb.mp3",
            "/AllMusic/A.mp3", "/AllMusic/a.mp3", "/AllMusic/é.mp3", "/AllMusic/é.mp3")
        val rows = paths.mapIndexed { index, path -> row(index.toString(), path) }
        assertEquals(LibraryDuplicateAudit(0, 0), LibraryDuplicateDiagnostics.inspect("source", rows))
    }
}
