package app.enotone.library

import app.enotone.database.RemoteTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PilotBudgetTest {
    private val key = LibrarySnapshotPlanner.accountKey("synthetic@example.test")

    private fun row(
        index: Int,
        extension: String = "flac",
        status: String? = null,
        title: String? = null,
        source: String = key,
    ): RemoteTrackEntity {
        val path = "/AllMusic/sample_$index.$extension"
        return RemoteTrackEntity(
            id = LibrarySnapshotPlanner.trackId(source, path),
            sourceKey = source, encodedPath = path,
            fileName = "sample_$index.$extension", remoteEtag = "\"v1\"",
            remoteSize = 4096, remoteModified = null,
            title = title, lastSeenEpoch = 1, tagScanCode = status,
        )
    }

    @Test fun selectsAtMostTenOrTwentyAndSkipsCompletedAndAlreadyTagged() {
        val all = (0 until 40).map { i ->
            when (i) {
                0 -> row(i, status = "FLAC_VORBIS")
                1 -> row(i, status = "NO_EMBEDDED_TAGS")
                2 -> row(i, title = "Previously scanned")
                3 -> row(i, extension = "ogg")
                4 -> row(i, source = LibrarySnapshotPlanner.accountKey("other@example.test"))
                else -> row(i, extension = if (i % 2 == 0) "mp3" else "flac")
            }
        }
        val ten = PilotBudget.select(key, all, 10)
        val twenty = PilotBudget.select(key, all, 20)
        assertEquals(10, ten.size)
        assertEquals(20, twenty.size)
        assertTrue(ten.all { it.tagScanCode == null && it.title == null })
        assertTrue(twenty.none { it.sourceKey != key || it.fileName.endsWith(".ogg") })
        assertEquals(10, ten.map { it.id }.distinct().size)
    }

    @Test fun retryableFailuresComeAfterNeverAttemptedAndResumeDoesNotRedoSuccess() {
        val pending = listOf(
            row(1, status = "RETRY_NETWORK"),
            row(2, status = "UNSUPPORTED_LAYOUT"),
            row(3),
            row(4, status = "RETRY_HTTP"),
        )
        val result = PilotBudget.select(key, pending, 10)
        assertEquals(3, result.size)
        assertEquals(null, result.first().tagScanCode)
        assertTrue(result.drop(1).all { it.tagScanCode!!.startsWith("RETRY_") })
        assertFalse(result.any { it.tagScanCode == "UNSUPPORTED_LAYOUT" })
    }

    @Test fun singleBadFileDoesNotCauseCompletedFilesToBeReprocessed() {
        val rows = (0 until 9).map { row(it, status = "FLAC_VORBIS") } +
            row(9, status = "FAILED_HTTP") +
            row(10, status = "RETRY_NETWORK") +
            (11 until 22).map { row(it) }
        val nextTen = PilotBudget.select(key, rows, 10)
        assertEquals(10, nextTen.size)
        assertTrue(nextTen.none { it.tagScanCode == "FLAC_VORBIS" })
        assertTrue(nextTen.none { it.tagScanCode == "FAILED_HTTP" })
        assertTrue(nextTen.all { it.tagScanCode == null })
        // A second manual pilot can revisit temporary failures only after fresh candidates.
        val retriesOnly = PilotBudget.select(key, rows.filter { it.tagScanCode != null }, 10)
        assertEquals(1, retriesOnly.size)
        assertEquals("RETRY_NETWORK", retriesOnly.single().tagScanCode)
    }

    @Test fun picksHundredFrom3590WithoutRescanningCompletedCandidates() {
        val library = (0 until 3590).map { index ->
            row(index, extension = if (index % 2 == 0) "mp3" else "flac",
                status = if (index < 10) "MP3_ID3" else null)
        }
        val first = PilotBudget.select(key, library, PilotBudget.RESUME_LIMIT)
        assertEquals(100, first.size)
        assertTrue(first.none { it.tagScanCode != null })
        assertEquals(100, first.map { it.id }.distinct().size)
        // Simulate a fully checkpointed session, then manual Resume.
        val finished = first.map { it.id }.toSet()
        val next = PilotBudget.select(key, library.map {
            if (it.id in finished) it.copy(tagScanCode = "MP3_ID3") else it
        }, PilotBudget.RESUME_LIMIT)
        assertEquals(100, next.size)
        assertTrue(next.none { it.id in finished })
        assertTrue(next.none { it.tagScanCode != null })
    }

    @Test fun queuedReindexCanSelectTaggedFilesWithoutTouchingOtherCompletedFiles() {
        val queued = row(1, status = "RETRY_REFRESH", title = "Old saved title")
        val failedRefresh = row(2, status = "RETRY_NETWORK", title = "Previous title")
        val complete = row(3, status = "FLAC_VORBIS", title = "Another track")
        val olderWithoutCheckpoint = row(4, title = "Old manual metadata")
        val newFile = row(5)
        val selected = PilotBudget.select(key,
            listOf(queued, failedRefresh, complete, olderWithoutCheckpoint, newFile), 10)
        assertEquals(3, selected.size)
        assertEquals(newFile.id, selected.first().id)
        assertTrue(selected.any { it.id == queued.id })
        assertTrue(selected.any { it.id == failedRefresh.id })
        assertFalse(selected.any { it.id == complete.id })
        assertFalse(selected.any { it.id == olderWithoutCheckpoint.id })
    }

    @Test fun doesNotTraverseNestedEntriesOrZeroSizedFiles() {
        val nested = row(1).copy(
            encodedPath = "/AllMusic/dir/sample_1.flac",
            id = LibrarySnapshotPlanner.trackId(key, "/AllMusic/dir/sample_1.flac"),
        )
        val missing = row(2).copy(missingScans = 1)
        val zero = row(3).copy(remoteSize = 0)
        assertTrue(PilotBudget.select(key, listOf(nested, missing, zero), 10).isEmpty())
    }

    @Test fun neverAllowsUnboundedPilotCountOrNetworkBudgets() {
        assertTrue(PilotBudget.validCount(10))
        assertTrue(PilotBudget.validCount(20))
        assertTrue(PilotBudget.validCount(PilotBudget.RESUME_LIMIT))
        assertFalse(PilotBudget.validCount(3590))
        assertFalse(PilotBudget.validCount(21))
        assertFalse(PilotBudget.validCount(99))
        assertFalse(PilotBudget.validCount(101))
        assertEquals(30, PilotBudget.requestCap(10))
        assertEquals(60, PilotBudget.requestCap(20))
        assertEquals(60, PilotBudget.requestCap(PilotBudget.RESUME_LIMIT))
        assertEquals(PilotBudget.MAX_BATCH_GETS, PilotBudget.requestCap(PilotBudget.RESUME_LIMIT))
        assertEquals(PilotBudget.MAX_BATCH_BYTES, PilotBudget.byteCap(PilotBudget.RESUME_LIMIT))
        assertEquals(180L * 1_000_000_000L, PilotBudget.MAX_BATCH_NANOS)
        assertEquals(4L * 1024 * 1024, PilotBudget.byteCap(20))
        assertFalse(PilotBudget.mayStartFile(60, 0, 20))
        assertFalse(PilotBudget.mayStartFile(0, PilotBudget.byteCap(20) - 65535L, 20))
        assertTrue(PilotBudget.mayStartFile(59, 0, 20))
        assertFalse(PilotBudget.mayStartFile(60, 0, PilotBudget.RESUME_LIMIT))
        assertFalse(PilotBudget.mayStartFile(0, 4L * 1024 * 1024, PilotBudget.RESUME_LIMIT))
        assertTrue(PilotBudget.mayStartFile(59, 0, PilotBudget.RESUME_LIMIT))
    }
}
