package app.enotone.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enotone.library.LibraryPage

/**
 * Draw-only Material 3 subtree, migrated without layout or dimension changes.
 * Scroll state belongs to Compose; data and actions belong to LibraryViewModel.
 */
@Composable
internal fun LibraryScreenContent(
    state: LibraryUiState,
    actions: LibraryScreenActions,
    artworkRepository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    onBack: () -> Unit,
    player: SharedPlayerUiState = SharedPlayerUiState(),
    onPlayerOpen: () -> Unit = {},
    onPlayerPlayPause: () -> Unit = {},
    onPlayerNext: () -> Unit = {},
) {
    val section = state.section
    val visiblePages = state.visiblePages
    val tracks = state.tracks
    val groups = state.groups
    val shownGroups = state.shownGroups
    val selectedGroup = state.selectedGroup
    val selectedGroupKey = state.selectedGroupKey
    val visible = state.visible
    val albumTracks = state.albumTracks
    val albumYear = state.albumYear
    val albumGenre = state.albumGenre
    val search = state.search
    val loading = state.loading
    val failed = state.failed
    val selecting = state.selecting
    val albumRefreshBusy = state.albumRefreshBusy
    val albumRefreshSampleIds = state.albumRefreshSampleIds
    val albumRefreshFeedback = state.albumRefreshFeedback
    val managedIndexStatus = state.managedScanStatus
    val artworkCatalog = state.artworkCatalog
    val playbackState = state.playbackState
    val miniVisible = player.playback.queueTotal > 0
    val libraryListState = remember(section, selectedGroupKey, search) { LazyListState() }
    val albumGridState = remember(section, search) { LazyGridState() }

    val albumRequest = state.confirmAlbumRefresh
    if (albumRequest != null) {
        AlertDialog(
            onDismissRequest = { actions.onDismissAlbumRefresh() },
            title = { Text(stringResource(R.string.album_refresh_confirm_title)) },
            text = { Text(stringResource(R.string.album_refresh_confirm_text,
                albumRequest.second.size)) },
            confirmButton = {
                TextButton(onClick = { actions.onConfirmAlbumRefresh() }) {
                    Text(stringResource(R.string.album_refresh_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { actions.onDismissAlbumRefresh() }) {
                    Text(stringResource(R.string.album_refresh_cancel))
                }
            },
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = {
                    if (selectedGroupKey != null) actions.onGroupSelected(null) else onBack()
                },
                modifier = Modifier.size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        androidx.compose.foundation.shape.CircleShape,
                    ),
            ) {
                Icon(painterResource(R.drawable.ic_action_back),
                    contentDescription = stringResource(R.string.library_group_back),
                    tint = MaterialTheme.colorScheme.onSurface)
            }

        }
        if (selectedGroupKey == null) {
            Text(
                text = stringResource(when (section) {
                    LibraryPage.TRACKS -> R.string.ui_songs_title
                    LibraryPage.ALBUMS -> R.string.library_tab_albums
                    LibraryPage.ARTISTS -> R.string.library_tab_artists
                    LibraryPage.ALBUM_ARTISTS -> R.string.library_tab_album_artists
                }),
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
            )
            BasicTextField(
                value = search,
                onValueChange = { actions.onSearch(it) },
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
                    .background(MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                decorationBox = { innerField ->
                    Box(modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart) {
                        if (search.isEmpty()) {
                            Text(
                                text = stringResource(when (section) {
                                    LibraryPage.TRACKS -> R.string.ui_search_songs
                                    LibraryPage.ALBUMS -> R.string.ui_search_albums
                                    LibraryPage.ARTISTS -> R.string.ui_search_artists
                                    LibraryPage.ALBUM_ARTISTS -> R.string.ui_search_artists
                                }),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 17.sp,
                            )
                        }
                        innerField()
                    }
                },
            )
        }
        if (search.isNotBlank()) {
            val count = if (section != LibraryPage.TRACKS && selectedGroup == null) {
                stringResource(R.string.library_group_count, shownGroups.size)
            } else {
                stringResource(R.string.library_count, visible.size,
                    if (section == LibraryPage.TRACKS) tracks.size
                    else selectedGroup?.trackIds?.size ?: 0)
            }
            Text(count, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if ((section == LibraryPage.TRACKS || section == LibraryPage.ALBUMS) &&
            selectedGroup == null && tracks.isNotEmpty() && search.isBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val enabled = state.playbackConnected && !selecting && appPassword.isNotBlank()
                MusicListAction(
                    text = stringResource(R.string.ui_play),
                    icon = R.drawable.ic_action_play,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { actions.onPlayAll() },
                )
                MusicListAction(
                    text = stringResource(R.string.library_shuffle_all),
                    icon = R.drawable.ic_action_shuffle,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { actions.onShuffleAll() },
                )
            }
        }
        when {
            loading -> Text(stringResource(R.string.library_loading))
            failed -> Text(stringResource(R.string.library_failed))
            tracks.isEmpty() -> Text(stringResource(R.string.library_empty))
            // Only the album page uses a cover grid; performers remain
            // readable lists until their detailed views are implemented.
            section == LibraryPage.ALBUMS && selectedGroup == null -> {
                if (shownGroups.isEmpty()) {
                    Text(stringResource(R.string.library_groups_empty))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = albumGridState,
                        contentPadding = PaddingValues(bottom = if (miniVisible) 184.dp else 100.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        gridItems(shownGroups, key = { it.key }) { group ->
                            AlbumArtworkTile(
                                group = group,
                                artworkGroup = artworkCatalog.byGroupKey[group.key],
                                repository = artworkRepository,
                                accountEmail = accountEmail,
                                appPassword = appPassword,
                                onOpen = { actions.onGroupSelected(group.key) },
                            )
                        }
                    }
                }
            }
            section != LibraryPage.TRACKS && selectedGroup == null -> {
                if (shownGroups.isEmpty()) {
                    Text(stringResource(R.string.library_groups_empty))
                } else {
                    LazyColumn(
                        state = libraryListState,
                        contentPadding = PaddingValues(bottom = if (miniVisible) 160.dp else 84.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(shownGroups, key = { it.key }) { group ->
                            Row(
                                modifier = Modifier.fillMaxWidth().height(72.dp)
                                    .clickable { actions.onGroupSelected(group.key) },
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.size(56.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = group.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = group.name,
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
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = 72.dp),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }
            else -> LazyColumn(
                state = libraryListState,
                        contentPadding = PaddingValues(bottom = if (miniVisible) 160.dp else 84.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (section == LibraryPage.ALBUMS && selectedGroup != null) {
                    item(key = "album-detail-header") {
                        AlbumDetailHeader(
                            group = selectedGroup,
                            year = albumYear,
                            genre = albumGenre,
                            artworkGroup = artworkCatalog.byGroupKey[selectedGroup.key],
                            repository = artworkRepository,
                            accountEmail = accountEmail,
                            appPassword = appPassword,
                            canPlay = albumTracks.isNotEmpty() && !selecting &&
                                !albumRefreshBusy &&
                                state.playbackConnected && appPassword.isNotBlank(),
                            onPlayAll = { actions.onAlbumPlayAll() },
                            onShuffleAll = { actions.onAlbumShuffleAll() },
                            refreshingTags = albumRefreshBusy,
                            canRefreshTags = !albumRefreshBusy &&
                                albumRefreshSampleIds.isNotEmpty() &&
                                appPassword.isNotBlank() &&
                                managedIndexStatus.state != ManagedScanState.RUNNING &&
                                !managedIndexStatus.terminating &&
                                playbackState !in setOf("PLAYING", "BUFFERING", "LOADING"),
                            refreshMessage = when {
                                albumRefreshFeedback?.first == selectedGroup.key ->
                                    albumRefreshFeedback?.second.orEmpty()
                                albumRefreshSampleIds.isEmpty() ->
                                    stringResource(R.string.album_refresh_no_more)
                                playbackState in setOf("PLAYING", "BUFFERING") ->
                                    stringResource(R.string.album_refresh_audio_active)
                                else -> ""
                            },
                            onRefreshTags = { actions.onRequestAlbumRefresh(selectedGroup.key, albumRefreshSampleIds) },
                        )
                    }
                }
                items(visible, key = { it.id }) { track ->
                    // All rows remain lazy. Cover loads only for composed items.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                            .clickable(enabled = !selecting && state.playbackConnected) {
                                actions.onTrackClicked(track, visible)
                            },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (section == LibraryPage.ALBUMS && selectedGroup != null) {
                            Text(
                                text = track.trackNumber?.toString().orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(width = 32.dp, height = 24.dp),
                            )
                        } else {
                            TrackArtworkThumbnail(
                                trackId = track.id,
                                artworkGroup = artworkCatalog.byTrackId[track.id],
                                repository = artworkRepository,
                                accountEmail = accountEmail,
                                appPassword = appPassword,
                                allowNetwork = playbackState != "LOADING" &&
                                    playbackState != "BUFFERING",
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            val subtitle = if (section == LibraryPage.ALBUMS &&
                                selectedGroup != null) track.artist.orEmpty()
                            else listOfNotNull(track.artist, track.album)
                                .joinToString(" · ")
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = if (section == LibraryPage.ALBUMS &&
                            selectedGroup != null) 44.dp else 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
    Column(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        EnoToneMiniPlayer(
            player = player, selecting = selecting,
            onOpen = onPlayerOpen,
            onPlayPause = onPlayerPlayPause,
            onNext = onPlayerNext,
        )
        // Material 3 NavigationBar; category visibility is managed in Settings.
        CompactLibraryNavigation(
            selectedPage = section,
            visiblePages = visiblePages,
            onHome = onBack,
            onPageSelected = { page -> actions.onPageSelected(page) },
            enabled = !selecting,
        )
    }
    }
}
