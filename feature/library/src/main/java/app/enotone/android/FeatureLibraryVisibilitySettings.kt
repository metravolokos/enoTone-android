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
fun FeatureLibraryVisibilitySettings(ids: LibraryVisibilityIds) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { LibraryPagePreferences(context) }
    var hiddenPages by remember(context) {
        mutableStateOf(preferences.hiddenPages())
    }
    FeatureLibraryVisibilitySettingsContent(hiddenPages, ids) { next ->
        hiddenPages = next
        preferences.saveHiddenPages(next)
    }
}

@Composable
fun FeatureLibraryVisibilitySettingsContent(hiddenPages: Set<LibraryPage>, ids: LibraryVisibilityIds, onHidden: (Set<LibraryPage>) -> Unit) {
    val visible = LibraryPageVisibility.visible(hiddenPages)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(ids.libraryPagesSettingsTitle),
            style = MaterialTheme.typography.titleMedium)
        LibraryPageVisibility.defaultOrder.forEach { page ->
            val title = when (page) {
                LibraryPage.TRACKS -> ids.libraryTabTracks
                LibraryPage.ALBUMS -> ids.libraryTabAlbums
                LibraryPage.ARTISTS -> ids.libraryTabArtists
                LibraryPage.ALBUM_ARTISTS -> ids.libraryTabAlbumArtists
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
                            onHidden(next)
                        }
                    },
                )
                Text(stringResource(title))
            }
        }
    }
}

