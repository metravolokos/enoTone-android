package app.enotone.library

import app.enotone.database.RemoteTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test

/**
 * Manual-only estimate for M6-0/7; no CI performance gate or Android claims.
 * To run locally: remove @Ignore in a private worktree, then execute the unit test.
 * Only synthetic tags are generated.
 */
class LibraryScaleBenchmarkTest {
    @Ignore("Manual measurement only; timing and heap differ across JDK and hardware")
    @Test fun catalogGroupingAndCollatorAtThreeSizes() {
        val sourceKey = "a".repeat(64)
        for (count in listOf(3_590, 20_000, 50_000)) {
            System.gc()
            val before = usedHeap()
            val remote = (0 until count).map { i ->
                RemoteTrackEntity(
                    id = "synthetic-" + i,
                    sourceKey = sourceKey,
                    encodedPath = "/AllMusic/synthetic_" + i + ".mp3",
                    fileName = "synthetic_" + i + ".mp3",
                    remoteEtag = null,
                    remoteSize = 123L,
                    remoteModified = null,
                    title = "Песня " + i,
                    artist = "Артист " + (i % 300),
                    album = "Альбом " + (i % 600),
                    albumArtist = "Автор " + (i % 100),
                    lastSeenEpoch = 1L,
                )
            }
            val start = System.nanoTime()
            val catalogue = OfflineTrackCatalog.build(sourceKey, remote)
            val albums = OfflineLibraryGrouping.albums(catalogue)
            val artists = OfflineLibraryGrouping.artists(catalogue)
            val albumArtists = OfflineLibraryGrouping.albumArtists(catalogue)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000L
            val memoryPerTrack = ((usedHeap() - before) / count).coerceAtLeast(0L)
            println(
                "synthetic=$count catalogMs=$elapsedMs approxHeapBytesPerTrack=$memoryPerTrack " +
                    "albums=" + albums.size + " artists=" + artists.size +
                    " authors=" + albumArtists.size,
            )
            assertEquals(count, catalogue.size)
        }
    }

    private fun usedHeap(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}
