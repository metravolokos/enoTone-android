package app.enotone.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.enotone.library.LibraryPage

/** Home uses genuine library destinations; no invented recommendations. */
@Composable
internal fun EnoToneHomeScreen(
    visiblePages: List<LibraryPage>,
    connectedAccount: Boolean,
    onSettings: () -> Unit,
    onOpenPage: (LibraryPage) -> Unit,
    miniPlayer: @Composable () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(78.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.nav_home),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSettings) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_settings),
                        contentDescription = stringResource(R.string.settings_title),
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_music_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                // An existing library action, not a fake editorial carousel.
                val page = visiblePages.firstOrNull()
                if (page != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .clickable {
                                if (connectedAccount) onOpenPage(page) else onSettings()
                            }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_albums),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(if (connectedAccount) R.string.home_open_library
                                else R.string.home_connect_account),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (connectedAccount) {
                    Text(
                        text = stringResource(R.string.home_library_sections),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    visiblePages.forEach { section ->
                        val (title, image) = when (section) {
                            LibraryPage.TRACKS -> R.string.library_tab_tracks to R.drawable.ic_nav_tracks
                            LibraryPage.ALBUMS -> R.string.library_tab_albums to R.drawable.ic_nav_albums
                            LibraryPage.ARTISTS -> R.string.library_tab_artists to R.drawable.ic_nav_artists
                            LibraryPage.ALBUM_ARTISTS -> R.string.library_tab_album_artists to R.drawable.ic_nav_album_artists
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().height(60.dp)
                                .clickable { onOpenPage(section) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                painter = painterResource(image),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(title),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "›",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()) {
            miniPlayer()
            CompactLibraryNavigation(
                selectedPage = null,
                visiblePages = visiblePages,
                onHome = {},
                onPageSelected = onOpenPage,
                enabled = connectedAccount,
            )
        }
    }
}
