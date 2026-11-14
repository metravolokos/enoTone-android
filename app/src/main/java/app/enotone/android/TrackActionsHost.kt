package app.enotone.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.enotone.android.core.ui.component.EnoAction
import app.enotone.android.core.ui.component.EnoActionSheet
import app.enotone.android.core.ui.component.EnoActionSheetHeader
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.library.OfflineTrackRow

/** Один лист для «⋮» и долгого нажатия во всех списках песен. */
@Composable
internal fun TrackActionsHost(
    track: OfflineTrackRow,
    tracks: List<OfflineTrackRow>,
    currentDetail: LibraryDetail?,
    artworkCatalog: AlbumArtworkCatalog,
    artworkRepository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    onDismiss: () -> Unit,
    onOpenDetail: (LibraryDetail) -> Unit,
) {
    val unknownAlbum = stringResource(R.string.library_unknown_album)
    val targets = remember(track.id, tracks, currentDetail, unknownAlbum) {
        TrackActionPlanner.targets(track, tracks, currentDetail, unknownAlbum)
    }
    val actions = listOfNotNull(
        targets.album?.let { group ->
            EnoAction(
                EnoIcons.ALBUMS,
                stringResource(R.string.song_action_go_album),
            ) { onOpenDetail(LibraryDetail.Album(group.key)) }
        },
        targets.artist?.let { group ->
            EnoAction(
                EnoIcons.ARTISTS,
                stringResource(R.string.song_action_go_artist),
            ) { onOpenDetail(LibraryDetail.Artist(group.key)) }
        },
    )
    EnoActionSheet(
        header = EnoActionSheetHeader(
            title = track.title,
            subtitle = listOfNotNull(track.artist, track.album).joinToString(" · "),
            artwork = {
                TrackArtworkThumbnail(
                    trackId = track.id,
                    artworkGroup = artworkCatalog.byTrackId[track.id],
                    repository = artworkRepository,
                    accountEmail = accountEmail,
                    appPassword = appPassword,
                    allowNetwork = false,
                    size = 56.dp,
                )
            },
        ),
        groups = listOf(actions),
        onDismiss = onDismiss,
    )
}
