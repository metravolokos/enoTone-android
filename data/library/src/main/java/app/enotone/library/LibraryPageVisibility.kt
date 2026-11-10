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

    /**
     * Settings can hide any page independently, but there must always be at
     * least one visible page. UI labels and their translations are not IDs.
     */
    fun withVisibility(
        hidden: Set<LibraryPage>,
        page: LibraryPage,
        shouldShow: Boolean,
    ): Set<LibraryPage> {
        val updated = if (shouldShow) hidden - page else hidden + page
        return if (visible(updated).none { it !in updated }) hidden else updated
    }

    /** Old/unknown identifiers from future or obsolete APKs are ignored. */
    fun parseHidden(stored: Set<String>): Set<LibraryPage> =
        stored.mapNotNull { name ->
            LibraryPage.entries.firstOrNull { it.name == name }
        }.toSet()

    /** New set for SharedPreferences; never expose an Android-owned mutable set. */
    fun persistedHidden(hidden: Set<LibraryPage>): Set<String> =
        hidden.mapTo(linkedSetOf()) { it.name }

    fun selectedOrFallback(
        requested: LibraryPage,
        visible: List<LibraryPage>,
    ): LibraryPage =
        if (requested in visible) requested else visible.firstOrNull() ?: LibraryPage.TRACKS
}
