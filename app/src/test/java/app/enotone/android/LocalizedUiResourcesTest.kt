package app.enotone.android

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runtime RU/EN resource lookup, not tests that search strings in source files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalizedUiResourcesTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()

    private fun inLanguage(language: String): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return base.createConfigurationContext(configuration)
    }

    @Test fun labelsForLibrarySettingsAndAuditResolveInBothLanguages() {
        val ids = listOf(
            R.string.home_title, R.string.home_hint, R.string.home_index_summary,
            R.string.library_pages_short, R.string.library_player_title,
            R.string.library_player_open, R.string.library_player_close,
            R.string.library_open_search, R.string.library_close_search,
            R.string.library_search_short, R.string.nav_home, R.string.settings_title,
            R.string.home_music_title, R.string.home_open_library,
            R.string.home_connect_account, R.string.library_pages_settings_button,
            R.string.library_pages_settings_title,
            R.string.library_pages_settings_explanation,
            R.string.library_pages_settings_last_visible,
            R.string.library_pages_settings_done, R.string.tag_audit_title,
            R.string.tag_audit_missing_album, R.string.tag_audit_missing_album_artist,
            R.string.library_reindex_action, R.string.library_reindex_confirm_title,
        )
        val ru = inLanguage("ru")
        val en = inLanguage("en")
        for (id in ids) {
            assertTrue("Empty RU label: $id", ru.getString(id).isNotBlank())
            assertTrue("Empty EN label: $id", en.getString(id).isNotBlank())
        }
        for (id in listOf(R.string.nav_home, R.string.settings_title,
            R.string.library_reindex_action, R.string.tag_audit_title,
            R.string.library_pages_settings_title)) {
            assertNotEquals("EN fallback to RU label: $id", ru.getString(id), en.getString(id))
        }
    }
}
