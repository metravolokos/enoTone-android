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
fun FeatureEnoToneHomeScreen(
    visiblePages: List<LibraryPage>,
    connectedAccount: Boolean,
    onSettings: () -> Unit,
    onOpenPage: (LibraryPage) -> Unit,
    miniPlayer: @Composable () -> Unit = {},
    ids: HomeScreenIds,
    navigation: @Composable () -> Unit,
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
                    text = stringResource(ids.navHome),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSettings) {
                    Icon(
                        painter = painterResource(ids.icActionSettings),
                        contentDescription = stringResource(ids.settingsTitle),
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
                    text = stringResource(ids.homeMusicTitle),
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
                            painter = painterResource(ids.icNavAlbums),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(if (connectedAccount) ids.homeOpenLibrary
                                else ids.homeConnectAccount),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (connectedAccount) {
                    Text(
                        text = stringResource(ids.homeLibrarySections),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    visiblePages.forEach { section ->
                        val (title, image) = when (section) {
                            LibraryPage.TRACKS -> ids.libraryTabTracks to ids.icNavTracks
                            LibraryPage.ALBUMS -> ids.libraryTabAlbums to ids.icNavAlbums
                            LibraryPage.ARTISTS -> ids.libraryTabArtists to ids.icNavArtists
                            LibraryPage.ALBUM_ARTISTS -> ids.libraryTabAlbumArtists to ids.icNavAlbumArtists
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
            navigation()
        }
    }
}
