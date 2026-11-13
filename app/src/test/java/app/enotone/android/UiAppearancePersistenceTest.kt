package app.enotone.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Поведение сохранённой настройки темы вместо тестов строк Graphite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UiAppearancePersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun restore() {
        UiAppearance.chooseMode(context, UiMode.SYSTEM)
    }

    @Test fun switchingModesPersistsExactlyAsBeforeD2() {
        val prefs = context.getSharedPreferences("enotone_ui_appearance_v1", Context.MODE_PRIVATE)
        for (mode in UiMode.entries) {
            UiAppearance.chooseMode(context, mode)
            assertEquals(mode, UiAppearance.mode)
            assertEquals(mode.name, prefs.getString("mode", null))
        }
    }
}
