package app.enotone.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.component.EnoBlurState
import app.enotone.android.core.ui.component.EnoCollapsingStrip
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.android.core.ui.component.EnoLetterAvatar
import app.enotone.android.core.ui.component.EnoPill
import app.enotone.android.core.ui.component.EnoRoundButton
import app.enotone.android.core.ui.component.EnoTrackRow
import app.enotone.android.core.ui.component.enoBlurSource
import app.enotone.android.core.ui.component.rememberEnoBlurState
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineTrackRow

/** Экран 08_artist, данные только из LibraryUiState; ни Room, ни сеть не запрашиваются для группировки. */
@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun ArtistDetailScreen(
    state: LibraryUiState,
    artist: OfflineLibraryGroup,
    actions: LibraryScreenActions,
    artworkRepository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    contentPadding: PaddingValues,
    onOpenDetail: (LibraryDetail) -> Unit,
    onBackDetail: () -> Unit,
    blur: EnoBlurState? = null,
) {
    val blurState = blur ?: rememberEnoBlurState()
    val unknownAlbum = stringResource(R.string.library_unknown_album)
    val plan = remember(artist, state.tracks, unknownAlbum) {
        ArtistDetailPlanner.plan(artist, state.tracks, unknownAlbum)
    }
    val listState = rememberLazyListState()
    val stripVisible by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 650
        }
    }
    var openedActions by remember { mutableStateOf<OfflineTrackRow?>(null) }
    val canPlay = plan.songs.isNotEmpty() && state.playbackConnected &&
        !state.selecting && appPassword.isNotBlank()
    val albumCount = pluralStringResource(
        R.plurals.library_albums_count, plan.albums.size, plan.albums.size.toString(),
    )
    val songCount = pluralStringResource(
        R.plurals.library_songs_count, plan.songs.size, plan.songs.size.toString(),
    )
    val caption = if (plan.compilationsOnly) {
        stringResource(R.string.artist_compilations_only, songCount)
    } else "$albumCount · $songCount"

    BackHandler { onBackDetail() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding().enoBlurSource(blurState),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
            item(key = "artist-head") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(48.dp))
                    EnoLetterAvatar(
                        name = artist.name,
                        contentDescription = artist.name,
                        size = 132.dp,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = EnoColors.text,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EnoColors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        EnoPill(
                            label = stringResource(R.string.ui_play),
                            onClick = { plan.songs.firstOrNull()?.let { actions.onTrackClicked(it, plan.songs) } },
                            modifier = Modifier.weight(1f),
                            accent = true, icon = EnoIcons.PLAY, enabled = canPlay,
                        )
                        EnoPill(
                            label = stringResource(R.string.library_shuffle_all),
                            onClick = {
                                val shuffled = plan.songs.shuffled()
                                shuffled.firstOrNull()?.let { actions.onTrackClicked(it, shuffled) }
                            },
                            modifier = Modifier.weight(1f),
                            icon = EnoIcons.SHUFFLE, enabled = canPlay,
                        )
                    }
                }
            }
            if (plan.albums.isNotEmpty()) {
                item(key = "artist-album-heading") {
                    ArtistSectionTitle(stringResource(R.string.library_tab_albums))
                }
                item(key = "artist-album-carousel") {
                    LazyRow(
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(plan.albums, key = { it.group.key }) { album ->
                            Box(Modifier.width(150.dp)) {
                                AlbumArtworkTile(
                                    group = album.group,
                                    artworkGroup = state.artworkCatalog.byGroupKey[album.group.key],
                                    repository = artworkRepository,
                                    accountEmail = accountEmail,
                                    appPassword = appPassword,
                                    onOpen = { onOpenDetail(LibraryDetail.Album(album.group.key)) },
                                    subtitle = album.year?.toString(),
                                    artistCarousel = true,
                                )
                            }
                        }
                    }
                }
            }
            item(key = "artist-tracks-heading") {
                ArtistSectionTitle(stringResource(R.string.ui_songs_title))
            }
            items(plan.songs, key = { it.id }) { track ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    EnoTrackRow(
                        title = track.title,
                        subtitle = track.album.orEmpty(),
                        onClick = {
                            if (canPlay) actions.onTrackClicked(track, plan.songs)
                        },
                        onMore = { openedActions = track },
                        onLongClick = { openedActions = track },
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
                    HorizontalDivider(color = EnoColors.line, modifier = Modifier.padding(start = 56.dp))
                }
            }
        }
        EnoCollapsingStrip(visible = stripVisible, title = artist.name, blur = blurState) {
            EnoRoundButton(
                icon = EnoIcons.SHUFFLE,
                contentDescription = stringResource(R.string.library_shuffle_all),
                onClick = {
                    val shuffled = plan.songs.shuffled()
                    shuffled.firstOrNull()?.let { actions.onTrackClicked(it, shuffled) }
                },
                enabled = canPlay,
            )
            EnoRoundButton(
                icon = EnoIcons.PLAY,
                contentDescription = stringResource(R.string.ui_play),
                onClick = { plan.songs.firstOrNull()?.let { actions.onTrackClicked(it, plan.songs) } },
                accent = true,
                enabled = canPlay,
            )
        }
    }
    openedActions?.let { track ->
        TrackActionsHost(
            track = track,
            tracks = state.tracks,
            currentDetail = LibraryDetail.Artist(artist.key),
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
private fun ArtistSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 14.dp),
        style = MaterialTheme.typography.titleMedium,
        color = EnoColors.text,
    )
}
