package app.enotone.android

import androidx.compose.runtime.Composable
import app.enotone.library.LibraryPage

/** The Home UI remains resource-identical; navigation is composed in :app. */
private val homeScreenIds = HomeScreenIds(
    navHome = R.string.nav_home,
    icActionSettings = R.drawable.ic_action_settings,
    settingsTitle = R.string.settings_title,
    homeMusicTitle = R.string.home_music_title,
    icNavAlbums = R.drawable.ic_nav_albums,
    homeOpenLibrary = R.string.home_open_library,
    homeConnectAccount = R.string.home_connect_account,
    homeLibrarySections = R.string.home_library_sections,
    libraryTabTracks = R.string.library_tab_tracks,
    icNavTracks = R.drawable.ic_nav_tracks,
    libraryTabAlbums = R.string.library_tab_albums,
    libraryTabArtists = R.string.library_tab_artists,
    icNavArtists = R.drawable.ic_nav_artists,
    libraryTabAlbumArtists = R.string.library_tab_album_artists,
    icNavAlbumArtists = R.drawable.ic_nav_album_artists,
)

@Composable
internal fun EnoToneHomeScreen(
    visiblePages: List<LibraryPage>,
    connectedAccount: Boolean,
    onSettings: () -> Unit,
    onOpenPage: (LibraryPage) -> Unit,
    miniPlayer: @Composable () -> Unit = {},
) {
    FeatureEnoToneHomeScreen(
        visiblePages = visiblePages,
        connectedAccount = connectedAccount,
        onSettings = onSettings,
        onOpenPage = onOpenPage,
        miniPlayer = miniPlayer,
        ids = homeScreenIds,
        navigation = {
            CompactLibraryNavigation(
                selectedPage = null,
                visiblePages = visiblePages,
                onHome = {},
                onPageSelected = onOpenPage,
                enabled = connectedAccount,
            )
        },
    )
}
