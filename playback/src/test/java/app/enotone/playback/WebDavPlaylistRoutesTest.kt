package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** No actual paths, accounts or tags: fixture files are synthetic. */
class WebDavPlaylistRoutesTest {
    private fun track(n: Int) = QueueTrack(
        id = "synthetic-$n",
        title = "Synthetic $n",
        encodedPath = "/AllMusic/test_$n.mp3",
        remoteSize = 1000L + n,
        remoteEtag = "\"etag-$n\"",
    )

    @Test fun bindsEachItemToItsOwnSizeAndEtagDuringNativeSkip() {
        val first = track(1)
        val second = track(2)
        val index = WebDavPlaylistRoutes.index(listOf(first, second))
        assertEquals(2, index.size)
        assertEquals(PlaybackRemoteFile(1001L, "\"etag-1\""),
            index[WebDavPlaybackTarget.url(first.encodedPath)])
        assertEquals(PlaybackRemoteFile(1002L, "\"etag-2\""),
            index[WebDavPlaybackTarget.url(second.encodedPath)])
        assertEquals(null, index["https://example.test/AllMusic/test_1.mp3"])
    }

    @Test fun validatesAllEntriesBeforeThePlayerCanStart() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavPlaylistRoutes.index(listOf(track(1), track(1).copy(id = "unique")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebDavPlaylistRoutes.index(listOf(track(1), track(2).copy(
                encodedPath = "/AllMusic/a%2Fb.mp3")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebDavPlaylistRoutes.index(listOf(track(1).copy(remoteSize = 0)))
        }
    }

    @Test fun indexesTheTenThousandAllowedQueueEntriesAndRejectsMore() {
        val tracks = (0 until 10_000).map(::track)
        val index = WebDavPlaylistRoutes.index(tracks)
        assertEquals(10_000, index.size)
        assertTrue(index.values.all { it.size > 0L })
        assertThrows(IllegalArgumentException::class.java) {
            WebDavPlaylistRoutes.index(tracks + track(10_000))
        }
    }
}
