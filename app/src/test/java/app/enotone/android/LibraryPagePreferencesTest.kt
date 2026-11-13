package app.enotone.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.enotone.library.LibraryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real local preferences, recreated separately from the UI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryPagePreferencesTest {
    @Test fun hiddenPagesPersistAcrossInstancesAndIgnoreUnknownIdentifiers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = context.getSharedPreferences(
            "enotone_library_navigation", Context.MODE_PRIVATE,
        )
        store.edit().clear().commit()
        try {
            val first = LibraryPagePreferences(context)
            assertTrue(first.hiddenPages().isEmpty())
            first.saveHiddenPages(setOf(LibraryPage.ARTISTS, LibraryPage.ALBUM_ARTISTS))
            assertEquals(setOf("ARTISTS", "ALBUM_ARTISTS"),
                store.getStringSet("hidden_pages_v1", emptySet()))
            assertEquals(setOf(LibraryPage.ARTISTS, LibraryPage.ALBUM_ARTISTS),
                LibraryPagePreferences(context).hiddenPages())
            store.edit().putStringSet("hidden_pages_v1",
                setOf("ALBUMS", "UNKNOWN_FUTURE_PAGE")).commit()
            assertEquals(setOf(LibraryPage.ALBUMS),
                LibraryPagePreferences(context).hiddenPages())
        } finally {
            store.edit().clear().commit()
        }
    }
}
