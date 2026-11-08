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
        assertFalse(PilotBudget.validCount(3590))
        assertFalse(PilotBudget.validCount(21))
        assertEquals(30, PilotBudget.requestCap(10))
        assertEquals(60, PilotBudget.requestCap(20))
        assertEquals(4L * 1024 * 1024, PilotBudget.byteCap(20))
        assertFalse(PilotBudget.mayStartFile(60, 0, 20))
        assertFalse(PilotBudget.mayStartFile(0, PilotBudget.byteCap(20) - 65535L, 20))
        assertTrue(PilotBudget.mayStartFile(59, 0, 20))
    }
}
