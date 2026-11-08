package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransientCachePolicyTest {
    private fun track(id: String, bytes: Long, etag: String? = null) = QueueTrack(
        id = id, title = "Synthetic", encodedPath = "/AllMusic/synthetic.mp3",
        remoteSize = bytes, remoteEtag = etag,
    )

    @Test fun cacheOnlyFitsWithinExplicitSizeAndFreeSpaceBounds() {
        val cap = TransientCachePolicy.MAX_CURRENT_BYTES
        val spare = TransientCachePolicy.MIN_FREE_AFTER_DOWNLOAD
        assertEquals(256L * 1024L * 1024L, cap)
        assertEquals(512L * 1024L * 1024L, TransientCachePolicy.MAX_DISK_BYTES)
        assertTrue(TransientCachePolicy.canDownload(cap, cap + spare))
        assertFalse(TransientCachePolicy.canDownload(cap + 1, cap + spare + 1))
        assertFalse(TransientCachePolicy.canDownload(0, cap + spare))
        assertFalse(TransientCachePolicy.canDownload(-1, cap + spare))
        assertFalse(TransientCachePolicy.canDownload(cap, cap + spare - 1))
    }

    @Test fun opaqueKeyNeverIncludesPathAndChangesOnFileVersion() {
        val first = TransientCachePolicy.key(track("id-1", 1024, "\"a\""))
        val identical = TransientCachePolicy.key(track("id-1", 1024, "\"a\""))
        assertEquals(first, identical)
        assertEquals(64, first.length)
        assertFalse(first.contains("AllMusic"))
        assertFalse(first.contains("synthetic"))
        assertNotEquals(first, TransientCachePolicy.key(track("id-1", 2048, "\"a\"")))
        assertNotEquals(first, TransientCachePolicy.key(track("id-1", 1024, "\"b\"")))
        assertNotEquals(first, TransientCachePolicy.key(track("id-2", 1024, "\"a\"")))
    }
}
