package app.enotone.android

import android.content.Context
import app.enotone.library.LibraryPage
import app.enotone.library.LibraryPageVisibility

/**
 * Local, non-sensitive UI preferences. Never touches the music database,
 * credentials, WebDAV or MediaSession. Stable enum names survive app upgrades.
 */
internal class LibraryPagePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "enotone_library_navigation", Context.MODE_PRIVATE,
    )

    fun hiddenPages(): Set<LibraryPage> =
        LibraryPageVisibility.parseHidden(
            prefs.getStringSet("hidden_pages_v1", emptySet())?.toSet().orEmpty(),
        )

    fun saveHiddenPages(hidden: Set<LibraryPage>) {
        prefs.edit().putStringSet(
            "hidden_pages_v1", LibraryPageVisibility.persistedHidden(hidden),
        ).apply()
    }
}
