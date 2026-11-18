package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.android.core.ui.component.EnoPill
import app.enotone.android.core.ui.component.EnoTrackRow
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineTrackRow
import java.text.NumberFormat
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
@Suppress("LongParameterList")
internal fun RecentlyAddedScreen(
    kind: RecentlyAddedKind,
    state: LibraryUiState?,
    actions: LibraryScreenActions,
    artworkRepository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    contentPadding: PaddingValues,
    onOpenDetail: (LibraryDetail) -> Unit,
) {
    val albums = state?.recentlyAddedAlbums.orEmpty()
        .take(RecentlyAddedPlanner.ALL_ALBUMS_LIMIT)
    val tracks = state?.recentlyAddedTracks.orEmpty()
        .take(RecentlyAddedPlanner.ALL_TRACKS_LIMIT)
    var openedActions by remember { mutableStateOf<OfflineTrackRow?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        RecentlyAddedHeader(
            kind = kind,
            albumCount = albums.size,
            trackCount = tracks.size,
            state = state,
            appPassword = appPassword,
            onPlay = {
                tracks.firstOrNull()?.let { actions.onTrackClicked(it, tracks) }
            },
            onShuffle = {
                val shuffled = tracks.shuffled()
                shuffled.firstOrNull()?.let { actions.onShuffleTracks(it, shuffled) }
            },
        )
        when {
            state == null || state.loading -> Text(stringResource(R.string.library_loading))
            state.failed -> Text(stringResource(R.string.library_failed))
            kind == RecentlyAddedKind.ALBUMS -> RecentlyAddedAlbumGrid(
                albums = albums,
                state = state,
                artworkRepository = artworkRepository,
                accountEmail = accountEmail,
                appPassword = appPassword,
                contentPadding = contentPadding,
                onOpenAlbum = { onOpenDetail(LibraryDetail.Album(it.key)) },
            )
            else -> RecentlyAddedSongs(
                tracks = tracks,
                state = state,
                actions = actions,
                artworkRepository = artworkRepository,
                accountEmail = accountEmail,
                appPassword = appPassword,
                contentPadding = contentPadding,
                onOpenActions = { openedActions = it },
            )
        }
    }
    openedActions?.let { track ->
        state?.let { loaded ->
            TrackActionsHost(
                track = track,
                groupIndex = loaded.groupIndex,
                actions = actions,
                currentDetail = null,
                artworkCatalog = loaded.artworkCatalog,
                artworkRepository = artworkRepository,
                accountEmail = accountEmail,
                appPassword = appPassword,
                onDismiss = { openedActions = null },
                onOpenDetail = onOpenDetail,
            )
        }
    }
}

@Composable
private fun RecentlyAddedHeader(
    kind: RecentlyAddedKind,
    albumCount: Int,
    trackCount: Int,
    state: LibraryUiState?,
    appPassword: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val count = if (kind == RecentlyAddedKind.ALBUMS) albumCount else trackCount
    val title = stringResource(
        if (kind == RecentlyAddedKind.ALBUMS) R.string.home_recently_added_albums
        else R.string.home_recently_added_songs,
    )
    val countLabel = when (kind) {
        RecentlyAddedKind.ALBUMS -> pluralStringResource(
            R.plurals.library_albums_count,
            count,
            NumberFormat.getIntegerInstance().format(count),
        )
        RecentlyAddedKind.TRACKS -> pluralStringResource(
            R.plurals.library_songs_count,
            count,
            NumberFormat.getIntegerInstance().format(count),
        )
    }
    Column(Modifier.padding(horizontal = 12.dp)) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp),
            color = EnoColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = countLabel,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
            color = EnoColors.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (kind == RecentlyAddedKind.TRACKS) {
            val enabled = state?.let { it.playbackConnected && !it.selecting } == true &&
                appPassword.isNotBlank()
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EnoPill(
                    label = stringResource(R.string.ui_play),
                    icon = EnoIcons.PLAY,
                    accent = true,
                    enabled = enabled && trackCount > 0,
                    modifier = Modifier.weight(1f),
                    onClick = onPlay,
                )
                EnoPill(
                    label = stringResource(R.string.library_shuffle_all),
                    icon = EnoIcons.SHUFFLE,
                    enabled = enabled && trackCount > 0,
                    modifier = Modifier.weight(1f),
                    onClick = onShuffle,
                )
            }
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun ColumnScope.RecentlyAddedAlbumGrid(
    albums: List<OfflineLibraryGroup>,
    state: LibraryUiState,
    artworkRepository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    contentPadding: PaddingValues,
    onOpenAlbum: (OfflineLibraryGroup) -> Unit,
) {
    if (albums.isEmpty()) {
        Text(stringResource(R.string.home_recently_added_empty_albums))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        gridItems(albums, key = { it.key }) { group ->
            AlbumArtworkTile(
                group = group,
                artworkGroup = state.artworkCatalog.byGroupKey[group.key],
                repository = artworkRepository,
                accountEmail = accountEmail,
                appPassword = appPassword,
                onOpen = { onOpenAlbum(group) },
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun ColumnScope.RecentlyAddedSongs(
    tracks: List<OfflineTrackRow>,
    state: LibraryUiState,
    actions: LibraryScreenActions,
    artworkRepository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    contentPadding: PaddingValues,
    onOpenActions: (OfflineTrackRow) -> Unit,
) {
    if (tracks.isEmpty()) {
        Text(stringResource(R.string.home_recently_added_empty_songs))
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tracks, key = { it.id }) { track ->
            RecentlyAddedSongRow(
                track = track,
                context = tracks,
                state = state,
                actions = actions,
                artworkRepository = artworkRepository,
                accountEmail = accountEmail,
                appPassword = appPassword,
                onOpenActions = onOpenActions,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun RecentlyAddedSongRow(
    track: OfflineTrackRow,
    context: List<OfflineTrackRow>,
    state: LibraryUiState,
    actions: LibraryScreenActions,
    artworkRepository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    onOpenActions: (OfflineTrackRow) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        EnoTrackRow(
            title = track.title,
            subtitle = listOfNotNull(track.artist, track.album).joinToString(" · "),
            onClick = {
                if (!state.selecting && state.playbackConnected) {
                    actions.onTrackClicked(track, context)
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
        HorizontalDivider(
            color = EnoColors.line,
            modifier = Modifier.padding(start = 56.dp),
            thickness = 0.5.dp,
        )
    }
}
