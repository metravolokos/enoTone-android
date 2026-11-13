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
            title = "Private title", artist = "Private artist",
            year = 2024, trackNumber = 3, discNumber = 2, tagScanCode = "FLAC_VORBIS")
            .copy(durationMs = 245_000L)
        val second = LibrarySnapshotPlanner.plan(account, listing(listOf(audio("v1"))), listOf(tagged), 2)
        assertEquals(1, first.inserted)
        assertEquals(1, second.unchanged)
        assertEquals("Private title", second.upserts.single().title)
        assertEquals("Private artist", second.upserts.single().artist)
        assertEquals(2024, second.upserts.single().year)
        assertEquals(3, second.upserts.single().trackNumber)
        assertEquals(2, second.upserts.single().discNumber)
        assertEquals("FLAC_VORBIS", second.upserts.single().tagScanCode)
        assertEquals(245_000L, second.upserts.single().durationMs)
        assertEquals(tagged.id, second.upserts.single().id)
        assertEquals(2L, second.upserts.single().lastSeenEpoch)
    }

    @Test fun genreIsPreservedForUnchangedRemoteVersionAndClearedWhenChanged() {
        val first = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("unchanged"))), emptyList(), 1)
        val tagged = first.upserts.single().copy(
            title = "Title", genre = "Jazz", tagScanCode = "FLAC_VORBIS")
        val unchanged = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("unchanged"))), listOf(tagged), 2)
        assertEquals("Jazz", unchanged.upserts.single().genre)
        val changed = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("replaced"))), unchanged.upserts, 3)
        assertNull(changed.upserts.single().genre)
        assertNull(changed.upserts.single().tagScanCode)
    }

    @Test fun incrementalRefreshAddsNewTrackWhilePreservingExistingTagsAndIds() {
        val original = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("etag-old"))), emptyList(), 1)
            .upserts.single().copy(
                title = "Original private title",
                album = "Original private album",
                tagScanCode = "FLAC_VORBIS",
            )
        val second = RemoteAudioFile("new-song.mp3",
            "/AllMusic/new-song.mp3", "etag-new", 12345L, null)
        val updated = LibrarySnapshotPlanner.plan(account,
            listing(listOf(audio("etag-old"), second)), listOf(original), 2)
        assertEquals(1, updated.inserted)
        assertEquals(1, updated.unchanged)
        assertEquals(0, updated.changed)
        val first = updated.upserts.first { it.id == original.id }
        assertEquals(original.title, first.title)
        assertEquals(original.album, first.album)
        assertEquals(original.tagScanCode, first.tagScanCode)
        val added = updated.upserts.single { it.fileName == "new-song.mp3" }
        assertNull(added.title)
        assertNull(added.tagScanCode)
        assertEquals(0, added.missingScans)
    }

    @Test fun changedEtagClearsTagsWithoutChangingId() {
        val old = LibrarySnapshotPlanner.plan(account, listing(listOf(audio("v1"))), emptyList(), 1)
            .upserts.single().copy(title = "private title", album = "private album",
                year = 2020, trackNumber = 10, discNumber = 2,
                tagScanCode = "FLAC_VORBIS", durationMs = 245_000L)
        val updated = LibrarySnapshotPlanner.plan(account, listing(listOf(audio("v2"))), listOf(old), 2)
        assertEquals(1, updated.changed)
        assertEquals(old.id, updated.upserts.single().id)
        assertNull(updated.upserts.single().title)
        assertNull(updated.upserts.single().album)
        assertNull(updated.upserts.single().year)
        assertNull(updated.upserts.single().trackNumber)
        assertNull(updated.upserts.single().discNumber)
        assertNull(updated.upserts.single().tagScanCode)
        assertNull(updated.upserts.single().durationMs)
    }

    @Test fun unchangedEtagWithChangedSizeOrMtimeQueuesFreshMetadata() {
        val old = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("stable", 50L, "a"))), emptyList(), 1)
            .upserts.single().copy(title = "Old", genre = "Rock",
                tagScanCode = "FLAC_VORBIS", durationMs = 245_000L)
        val larger = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("stable", 100L, "a"))), listOf(old), 2)
        assertEquals(1, larger.changed)
        assertEquals(old.id, larger.upserts.single().id)
        assertNull(larger.upserts.single().title)
        assertNull(larger.upserts.single().genre)
        assertNull(larger.upserts.single().tagScanCode)
        assertNull(larger.upserts.single().durationMs)
        val touched = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("stable", 50L, "b"))), listOf(old), 2)
        assertEquals(1, touched.changed)
        assertNull(touched.upserts.single().tagScanCode)
        val same = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("stable", 50L, "a"))), listOf(old), 2)
        assertEquals(1, same.unchanged)
        assertEquals("Rock", same.upserts.single().genre)
        assertEquals(245_000L, same.upserts.single().durationMs)
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

    @Test fun changedIdsListOnlyNewVersionsIncludingMtimeOnlyChange() {
        val old = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("stable", 50L, "a"))), emptyList(), 1).upserts.single()
        val touched = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("stable", 50L, "b"))), listOf(old), 2)
        // Тот же ETag и размер, новое mtime: ключ обложки прежний, поэтому её ссылку надо сбросить.
        assertEquals(listOf(old.id), touched.changedIds)
        val same = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("stable", 50L, "a"))), listOf(old), 2)
        assertTrue(same.changedIds.isEmpty())
        val fresh = LibrarySnapshotPlanner.plan(account, listing(listOf(audio("v1"))), emptyList(), 1)
        assertTrue(fresh.changedIds.isEmpty())
    }

    @Test fun firstSeenIsUnknownOnFirstImportSetForNewFilesAndKeptOnChange() {
        val first = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("v1"))), emptyList(), 1, nowEpochMs = 111L)
        assertNull(first.upserts.single().firstSeenEpochMs)
        val other = RemoteAudioFile("new.flac", "/AllMusic/new.flac", "n1", 50L, null)
        val second = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("v1"), other)), first.upserts, 2, nowEpochMs = 222L)
        val byPath = second.upserts.associateBy { it.encodedPath }
        assertNull(byPath.getValue("/AllMusic/song.flac").firstSeenEpochMs)
        assertEquals(222L, byPath.getValue("/AllMusic/new.flac").firstSeenEpochMs)
        val changed = LibrarySnapshotPlanner.plan(
            account, listing(listOf(audio("v1"), other.copy(etag = "n2"))), second.upserts, 3,
            nowEpochMs = 333L)
        assertEquals(222L, changed.upserts.single { it.encodedPath == "/AllMusic/new.flac" }.firstSeenEpochMs)
    }
}
