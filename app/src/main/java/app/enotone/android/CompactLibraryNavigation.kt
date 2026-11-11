package app.enotone.android

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.enotone.library.LibraryPage

/**
 * Native Material 3 NavigationBar: actual destinations only.
 * Respects saved page visibility and selection; no Radio/Search placeholder.
 */
@Composable
internal fun CompactLibraryNavigation(
    selectedPage: LibraryPage?,
    visiblePages: List<LibraryPage>,
    onHome: () -> Unit,
    onPageSelected: (LibraryPage) -> Unit,
    enabled: Boolean = true,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = androidx.compose.ui.unit.Dp.Unspecified,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        val destinations = listOf<LibraryPage?>(null) + visiblePages
        destinations.forEach { page ->
            val label = when (page) {
                null -> R.string.nav_home
                LibraryPage.TRACKS -> R.string.library_tab_tracks
                LibraryPage.ALBUMS -> R.string.library_tab_albums
                LibraryPage.ARTISTS -> R.string.library_tab_artists
                LibraryPage.ALBUM_ARTISTS -> R.string.library_tab_album_artists
            }
            val icon = when (page) {
                null -> R.drawable.ic_nav_home
                LibraryPage.TRACKS -> R.drawable.ic_nav_tracks
                LibraryPage.ALBUMS -> R.drawable.ic_nav_albums
                LibraryPage.ARTISTS -> R.drawable.ic_nav_artists
                LibraryPage.ALBUM_ARTISTS -> R.drawable.ic_nav_album_artists
            }
            NavigationBarItem(
                selected = selectedPage == page,
                enabled = enabled,
                onClick = {
                    if (page == null) onHome() else onPageSelected(page)
                },
                icon = {
                    Icon(painterResource(icon), contentDescription = null)
                },
                label = {
                    Text(stringResource(label), maxLines = 1)
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
