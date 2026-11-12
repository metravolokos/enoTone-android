package app.enotone.library

import app.enotone.database.RemoteTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogMapperTest {
    private fun row(id: String, source: String, size: Long?, missing: Int = 0) =
        RemoteTrackEntity(
            id = id, sourceKey = source, encodedPath = "/AllMusic/$id.mp3",
            fileName = "$id.mp3", remoteEtag = null, remoteSize = size,
            remoteModified = null, lastSeenEpoch = 0L, missingScans = missing,
            title = "Song $id",
        )

    @Test fun mapperKeepsOnlyPlayableCurrentSourceInQueue() {
        val result = LibraryCatalogMapper.map("one", listOf(
            row("a", "one", 100L),
            row("b", "one", null),
            row("c", "one", 20L, missing = 1),
            row("d", "two", 100L),
        ))
        assertEquals(setOf("a", "b"), result.tracks.map { it.id }.toSet())
        assertEquals(setOf("a"), result.preparedById.keys)
        assertEquals("Song a", result.preparedById.getValue("a").title)
        assertFalse("c" in result.preparedById)
        assertTrue(result.preparedById.values.all { it.remoteSize > 0L })
    }
}
