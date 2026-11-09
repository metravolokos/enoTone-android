package app.enotone.library

import app.enotone.database.RemoteTrackEntity
import app.enotone.network.FlatWebDavListing
import app.enotone.network.RemoteAudioFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySnapshotPlannerTest {
    private val account = LibrarySnapshotPlanner.accountKey("Example@Mail.test")

    private fun audio(etag: String?, size: Long? = 100L, modified: String? = null) =
        RemoteAudioFile("song.flac", "/AllMusic/song.flac", etag, size, modified)

    private fun listing(
        items: List<RemoteAudioFile>,
        invalid: Int = 0,
        folders: Int = 0,
    ) = FlatWebDavListing(items, folders, 0, 0, invalid)

    @Test fun identicalEtagPreservesTagsAndStableIdentity() {
        val first = LibrarySnapshotPlanner.plan(account, listing(listOf(audio("v1"))), emptyList(), 1)
        val tagged = first.upserts.single().copy(
            title = "Private title", artist = "Private artist", tagScanCode = "FLAC_VORBIS")
        val second = LibrarySnapshotPlanner.plan(account, listing(listOf(audio("v1"))), listOf(tagged), 2)
        assertEquals(1, first.inserted)
        assertEquals(1, second.unchanged)
        assertEquals("Private title", second.upserts.single().title)
        assertEquals("Private artist", second.upserts.single().artist)
        assertEquals("FLAC_VORBIS", second.upserts.single().tagScanCode)
        assertEquals(tagged.id, second.upserts.single().id)
        assertEquals(2L, second.upserts.single().lastSeenEpoch)
    }

    @Test fun changedEtagClearsTagsWithoutChangingId() {
        val old = LibrarySnapshotPlanner.plan(account, listing(listOf(audio("v1"))), emptyList(), 1)
            .upserts.single().copy(title = "private title", album = "private album",
                tagScanCode = "FLAC_VORBIS")
        val updated = LibrarySnapshotPlanner.plan(account, listing(listOf(audio("v2"))), listOf(old), 2)
        assertEquals(1, updated.changed)
        assertEquals(old.id, updated.upserts.single().id)
        assertNull(updated.upserts.single().title)
        assertNull(updated.upserts.single().album)
        assertNull(updated.upserts.single().tagScanCode)
    }

    @Test fun incrementalListAddsOnlyNewFilesAndPreservesUnchangedTagCheckpoints() {
        val existing = LibrarySnapshotPlanner.plan(account,
            listing(listOf(audio("v1"))), emptyList(), 1).upserts.single()
            .copy(title = "Existing title", album = "Existing album",
                tagScanCode = "FLAC_VORBIS")
        val newlyAdded = RemoteAudioFile("fresh.flac", "/AllMusic/fresh.flac",
            "\"fresh-1\"", 1234L, null)
        val next = LibrarySnapshotPlanner.plan(account,
            listing(listOf(audio("v1"), newlyAdded)), listOf(existing), 2)
        assertEquals(1, next.inserted)
        assertEquals(0, next.changed)
        assertEquals(1, next.unchanged)
        val prior = next.upserts.first { it.id == existing.id }
        assertEquals("Existing album", prior.album)
        assertEquals("FLAC_VORBIS", prior.tagScanCode)
        val fresh = next.upserts.first { it.id != existing.id }
        assertNull(fresh.tagScanCode)
        assertNull(fresh.album)
        assertEquals(0, fresh.missingScans)
    }

    @Test fun unchangedListingPreservesRequestedTagRefreshMarker() {
        val old = LibrarySnapshotPlanner.plan(account,
            listing(listOf(audio("v1"))), emptyList(), 1)
            .upserts.single().copy(
                title = "Visible old title", album = "Visible old album",
                tagScanCode = "RETRY_REFRESH",
            )
        val next = LibrarySnapshotPlanner.plan(account,
            listing(listOf(audio("v1"))), listOf(old), 2)
        assertEquals("RETRY_REFRESH", next.upserts.single().tagScanCode)
        assertEquals("Visible old album", next.upserts.single().album)
    }

    @Test fun missingEtagUsesSizeAndModifiedOrMarksChanged() {
        val first = LibrarySnapshotPlanner.plan(account,
            listing(listOf(audio(null, 50L, "Sun, 08 Nov 2026 00:00:00 GMT"))), emptyList(), 1)
        val second = LibrarySnapshotPlanner.plan(account,
            listing(listOf(audio(null, 50L, "Sun, 08 Nov 2026 00:00:00 GMT"))), first.upserts, 2)
        assertEquals(1, second.unchanged)
        val unreliable = LibrarySnapshotPlanner.plan(account, listing(listOf(audio(null, 50L))),
            first.upserts, 3)
        assertEquals(1, unreliable.changed)
    }

    @Test fun emptyOrMalformedListingNeverCreatesDestructiveAction() {
        val first = LibrarySnapshotPlanner.plan(account, listing(listOf(audio("v1"))), emptyList(), 1)
        val later = LibrarySnapshotPlanner.plan(account, listing(emptyList(), invalid = 1),
            first.upserts, 2)
        assertEquals(0, later.upserts.size)
        assertEquals(1, later.previouslyMissing)
        assertFalse(later.completeForAbsenceTracking)
    }

    @Test fun separateAccountsHaveSeparateIdentifiersAndNoRawEmail() {
        val second = LibrarySnapshotPlanner.accountKey("other@mail.test")
        assertNotEquals(account, second)
        assertNotEquals(LibrarySnapshotPlanner.trackId(account, "/AllMusic/song.flac"),
            LibrarySnapshotPlanner.trackId(second, "/AllMusic/song.flac"))
        assertFalse(account.contains("@"))
        assertEquals(account, LibrarySnapshotPlanner.accountKey("  EXAMPLE@mail.test "))
    }

    @Test fun rejectsNonflatFileEvenIfCallerBypassedParser() {
        try {
            LibrarySnapshotPlanner.plan(account,
                listing(listOf(RemoteAudioFile("bad.flac", "/AllMusic/dir/bad.flac", null, null, null))),
                emptyList(), 1)
            throw AssertionError("Expected rejection")
        } catch (e: IllegalArgumentException) {
            assertFalse(e.message.orEmpty().contains("bad.flac"))
        }
    }
}
