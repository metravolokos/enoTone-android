package app.enotone.android

import app.enotone.library.LibraryPage
import org.junit.Assert.assertNull
import org.junit.Test

/** Home/не-library route не имеет target: нельзя вызывать pendingRoute с requireNotNull. */
class AppLibraryRouteSnapshotsTest {
    @Test fun homeDoesNotRequestPendingLibraryState() {
        val home = AppNavState()
        val library = LibraryUiState(section = LibraryPage.ALBUM_ARTISTS)
        val snapshots = AppLibraryRouteSnapshots()
        assertNull(snapshots.stateFor(home, home, library))
    }
}
