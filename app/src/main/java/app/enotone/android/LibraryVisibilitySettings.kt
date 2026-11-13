package app.enotone.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.enotone.library.LibraryPage

/** All application R resources stay in :app, unchanged until D2. */
private val visibilityIds = LibraryVisibilityIds(
    libraryPagesSettingsTitle = R.string.library_pages_settings_title,
    libraryTabTracks = R.string.library_tab_tracks,
    libraryTabAlbums = R.string.library_tab_albums,
    libraryTabArtists = R.string.library_tab_artists,
    libraryTabAlbumArtists = R.string.library_tab_album_artists,
)

/** Preserves SettingsScreen's public-to-app entry point. */
@Composable
internal fun LibraryVisibilitySettings() {
    FeatureLibraryVisibilitySettings(visibilityIds)
}

/** Keeps the 5a Compose test entry point and callbacks unchanged. */
@Composable
internal fun LibraryVisibilitySettingsContent(
    hiddenPages: Set<LibraryPage>,
    onHidden: (Set<LibraryPage>) -> Unit,
) {
    FeatureLibraryVisibilitySettingsContent(hiddenPages, visibilityIds, onHidden)
}

@Preview
@Composable
internal fun LibraryVisibilitySettingsPreview() {
    MaterialTheme { LibraryVisibilitySettingsContent(emptySet()) {} }
}
