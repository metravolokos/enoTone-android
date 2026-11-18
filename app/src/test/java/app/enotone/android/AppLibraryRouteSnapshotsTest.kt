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
        val otherRoutes = listOf(
            home, AppNavigator.openSettings(home), AppNavigator.openDiagnostics(home),
        )
        otherRoutes.forEach { route ->
            assertNull(route.libraryTarget())
            assertNull(snapshots.stateFor(route, route, library))
        }
        val recent = AppNavigator.push(
            home,
            LibraryDetail.RecentlyAdded(RecentlyAddedKind.TRACKS),
        )
        assertNull(recent.libraryTarget())
        assertNull(snapshots.stateFor(recent, recent, library))
    }
}
