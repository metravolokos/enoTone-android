package app.enotone.android

import app.enotone.library.LibraryPage

/** Closing the player is the final step, after resolution and navigation both succeed. */
internal class PlayerDetailOpener(
    private val resolve: suspend (String, LibraryPage) -> LibraryDetail?,
    private val currentTrackId: () -> String?,
    private val navigate: (LibraryDetail) -> Boolean,
    private val close: () -> Unit,
    private val unavailable: () -> Unit,
) {
    suspend fun open(trackId: String, page: LibraryPage) {
        val destination = resolve(trackId, page)
        // A user may close the player or change songs while Room is loading.
        if (currentTrackId() != trackId) return
        if (destination != null && navigate(destination)) close() else unavailable()
    }
}
