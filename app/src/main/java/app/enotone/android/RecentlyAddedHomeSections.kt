package app.enotone.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enotone.android.core.ui.component.EnoTrackRow
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineTrackRow

@Composable
@Suppress("LongParameterList")
internal fun RecentlyAddedHomeSections(
    state: LibraryUiState?,
    actions: LibraryScreenActions,
    artworkRepository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    onOpenDetail: (LibraryDetail) -> Unit,
) {
    if (state == null || state.loading || state.failed) return
    val albums = state.recentlyAddedAlbums.take(RecentlyAddedPlanner.HOME_ALBUMS_LIMIT)
    val tracks = state.recentlyAddedTracks.take(RecentlyAddedPlanner.HOME_TRACKS_LIMIT)
    if (albums.isEmpty() && tracks.isEmpty()) return

    var openedActions by remember { mutableStateOf<OfflineTrackRow?>(null) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (albums.isNotEmpty()) {
            RecentlyAddedAlbumCarousel(
                albums = albums,
                state = state,
                artworkRepository = artworkRepository,
                accountEmail = accountEmail,
                appPassword = appPassword,
                onAll = { onOpenDetail(LibraryDetail.RecentlyAdded(RecentlyAddedKind.ALBUMS)) },
                onOpenAlbum = { onOpenDetail(LibraryDetail.Album(it.key)) },
            )
        }
        if (tracks.isNotEmpty()) {
            RecentlyAddedTrackList(
                tracks = tracks,
                state = state,
                actions = actions,
                artworkRepository = artworkRepository,
                accountEmail = accountEmail,
                appPassword = appPassword,
                onAll = { onOpenDetail(LibraryDetail.RecentlyAdded(RecentlyAddedKind.TRACKS)) },
                onOpenActions = { openedActions = it },
            )
        }
    }
    openedActions?.let { track ->
        TrackActionsHost(
            track = track,
            groupIndex = state.groupIndex,
            actions = actions,
            currentDetail = null,
            artworkCatalog = state.artworkCatalog,
            artworkRepository = artworkRepository,
            accountEmail = accountEmail,
            appPassword = appPassword,
            onDismiss = { openedActions = null },
            onOpenDetail = onOpenDetail,
        )
    }
}

@Composable
private fun RecentlyAddedAlbumCarousel(
    albums: List<OfflineLibraryGroup>,
    state: LibraryUiState,
    artworkRepository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    onAll: () -> Unit,
    onOpenAlbum: (OfflineLibraryGroup) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RecentlyAddedSectionHeading(
            title = stringResource(R.string.home_recently_added_albums),
            onAll = onAll,
        )
        LazyRow(
            contentPadding = PaddingValues(start = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(albums, key = { it.key }) { group ->
                Box(Modifier.width(150.dp)) {
                    AlbumArtworkTile(
                        group = group,
                        artworkGroup = state.artworkCatalog.byGroupKey[group.key],
                        repository = artworkRepository,
                        accountEmail = accountEmail,
                        appPassword = appPassword,
                        onOpen = { onOpenAlbum(group) },
                        useCoreTile = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentlyAddedTrackList(
    tracks: List<OfflineTrackRow>,
    state: LibraryUiState,
    actions: LibraryScreenActions,
    artworkRepository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    onAll: () -> Unit,
    onOpenActions: (OfflineTrackRow) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RecentlyAddedSectionHeading(
            title = stringResource(R.string.home_recently_added_songs),
            onAll = onAll,
        )
        tracks.forEachIndexed { index, track ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                EnoTrackRow(
                    title = track.title,
                    subtitle = listOfNotNull(track.artist, track.album).joinToString(" · "),
                    onClick = {
                        if (!state.selecting && state.playbackConnected) {
                            actions.onTrackClicked(track, tracks)
                        }
                    },
                    onMore = { onOpenActions(track) },
                    onLongClick = { onOpenActions(track) },
                    onLongClickLabel = stringResource(R.string.song_actions_long_press),
                    moreContentDescription = stringResource(R.string.song_actions_description, track.title),
                    playingDescription = stringResource(R.string.song_playing_description),
                    isCurrent = track.id == state.currentTrackId,
                    isPlaying = state.playbackState == "PLAYING",
                    artwork = {
                        TrackArtworkThumbnail(
                            trackId = track.id,
                            artworkGroup = state.artworkCatalog.byTrackId[track.id],
                            repository = artworkRepository,
                            accountEmail = accountEmail,
                            appPassword = appPassword,
                            allowNetwork = state.playbackState !in setOf("LOADING", "BUFFERING"),
                        )
                    },
                )
                if (index != tracks.lastIndex) {
                    HorizontalDivider(
                        color = EnoColors.line,
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentlyAddedSectionHeading(title: String, onAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = EnoColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.home_recently_added_all),
            modifier = Modifier.clickable(role = Role.Button, onClick = onAll)
                .padding(vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            color = EnoColors.accentText,
        )
    }
}
