package app.enotone.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.enotone.library.LibraryPage

/**
 * Exactly five positions when all pages are visible: Home + four library pages.
 * Hidden categories disappear, Home never does. A compact 52dp rail is shared
 * by Home and library; all touch targets are 48dp and announced to TalkBack.
 */
@Composable
internal fun CompactLibraryNavigation(
    selectedPage: LibraryPage?,
    visiblePages: List<LibraryPage>,
    onHome: () -> Unit,
    onPageSelected: (LibraryPage) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val destinations = listOf<LibraryPage?>(null) + visiblePages
        destinations.forEach { page ->
            val title = when (page) {
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
            val label = stringResource(title)
            val active = selectedPage == page
            Box(
                modifier = Modifier.weight(1f).height(48.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent,
                        RoundedCornerShape(16.dp),
                    )
                    .selectable(
                        selected = active,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = {
                            if (!active) {
                                if (page == null) onHome() else onPageSelected(page)
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = label,
                    tint = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
