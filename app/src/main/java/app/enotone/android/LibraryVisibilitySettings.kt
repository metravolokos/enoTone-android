package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.enotone.library.LibraryPage
import app.enotone.library.LibraryPageVisibility

/** Persistent settings, never a control on the music browsing page. */
@Composable
internal fun LibraryVisibilitySettings() {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { LibraryPagePreferences(context) }
    var hiddenPages by remember(context) {
        mutableStateOf(preferences.hiddenPages())
    }
    val visible = LibraryPageVisibility.visible(hiddenPages)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.library_pages_settings_title),
            style = MaterialTheme.typography.titleMedium)
        LibraryPageVisibility.defaultOrder.forEach { page ->
            val title = when (page) {
                LibraryPage.TRACKS -> R.string.library_tab_tracks
                LibraryPage.ALBUMS -> R.string.library_tab_albums
                LibraryPage.ARTISTS -> R.string.library_tab_artists
                LibraryPage.ALBUM_ARTISTS -> R.string.library_tab_album_artists
            }
            val isVisible = page in visible
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = isVisible,
                    enabled = !isVisible || visible.size > 1,
                    onCheckedChange = { shouldShow ->
                        val next = LibraryPageVisibility.withVisibility(
                            hiddenPages, page, shouldShow,
                        )
                        if (next != hiddenPages) {
                            hiddenPages = next
                            preferences.saveHiddenPages(next)
                        }
                    },
                )
                Text(stringResource(title))
            }
        }
    }
}
