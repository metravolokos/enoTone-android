package app.enotone.library

/**
 * Stable page IDs for a future Settings control. Hidden pages are a
 * presentation preference, not an instruction to delete/index music.
 */
enum class LibraryPage { TRACKS, ALBUMS, ARTISTS, ALBUM_ARTISTS }

object LibraryPageVisibility {
    val defaultOrder = listOf(
        LibraryPage.TRACKS,
        LibraryPage.ALBUMS,
        LibraryPage.ARTISTS,
        LibraryPage.ALBUM_ARTISTS,
    )

    /** At least the Tracks page stays reachable if every page was hidden. */
    fun visible(hidden: Set<LibraryPage>): List<LibraryPage> =
        defaultOrder.filterNot { it in hidden }.ifEmpty { listOf(LibraryPage.TRACKS) }

    fun selectedOrFallback(
        requested: LibraryPage,
        visible: List<LibraryPage>,
    ): LibraryPage =
        if (requested in visible) requested else visible.firstOrNull() ?: LibraryPage.TRACKS
}
