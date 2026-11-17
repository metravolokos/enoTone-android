package app.enotone.android

import app.enotone.library.LibraryPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDetailOpenerTest {
    @Test fun missingOrRejectedDestinationKeepsPlayerAndReportsFailure() = runTest {
        for (resolved in listOf(null, LibraryDetail.Album("album:fixture"))) {
            var closed = false
            var failures = 0
            val opener = PlayerDetailOpener({ _, _ -> resolved }, { "track" },
                { false }, { closed = true }, { failures++ })
            opener.open("track", LibraryPage.ALBUMS)
            assertFalse(closed)
            assertEquals(1, failures)
        }
    }

    @Test fun coldStartWaitsAndClosesOnlyAfterNavigationAccepts() = runTest {
        val ready = CompletableDeferred<LibraryDetail>()
        var navigated = false
        var closed = false
        val opener = PlayerDetailOpener({ _, _ -> ready.await() }, { "track" },
            {
                navigated = true
                true
            }, {
                assertTrue(navigated)
                closed = true
            }, { error("Unexpected") })
        val request = async { opener.open("track", LibraryPage.ARTISTS) }
        runCurrent()
        assertFalse(navigated)
        assertFalse(closed)
        ready.complete(LibraryDetail.Artist("artist:fixture"))
        request.await()
        assertTrue(closed)
    }

    @Test fun changingTrackOrClosingPlayerWhileLoadingCancelsNavigation() = runTest {
        for (current in listOf(null, "next")) {
            val opener = PlayerDetailOpener({ _, _ -> LibraryDetail.Album("album:fixture") },
                { current }, { error("Stale navigation") }, { error("Stale close") }, { error("Stale feedback") })
            opener.open("previous", LibraryPage.ALBUMS)
        }
    }
}
