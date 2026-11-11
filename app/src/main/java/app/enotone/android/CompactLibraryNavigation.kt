package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enotone.library.LibraryPage

/**
 * The tab bar is for actual destinations only: Home and enabled library pages.
 * 60dp row + a labeled 24dp symbol, with a >= 48dp touch target. No fake Radio
 * or New tab exists until their functions exist. Saved visibility is honored.
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
        modifier = Modifier.fillMaxWidth().height(62.dp).selectableGroup(),
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
            val image = when (page) {
                null -> R.drawable.ic_nav_home
                LibraryPage.TRACKS -> R.drawable.ic_nav_tracks
                LibraryPage.ALBUMS -> R.drawable.ic_nav_albums
                LibraryPage.ARTISTS -> R.drawable.ic_nav_artists
                LibraryPage.ALBUM_ARTISTS -> R.drawable.ic_nav_album_artists
            }
            val label = stringResource(title)
            val active = selectedPage == page
            val tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier.weight(1f).height(60.dp)
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    painter = painterResource(image),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = label,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
