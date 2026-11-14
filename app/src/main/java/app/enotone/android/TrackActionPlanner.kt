package app.enotone.android

import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineLibraryGrouping
import app.enotone.library.OfflineTrackRow

/** Группы из тех же тегов и тем же алгоритмом, что вкладки «Альбомы» и «Артисты». */
internal data class TrackActionTargets(
    val album: OfflineLibraryGroup?,
    val artist: OfflineLibraryGroup?,
)

internal object TrackActionPlanner {
    fun targets(
        track: OfflineTrackRow,
        library: List<OfflineTrackRow>,
        current: LibraryDetail?,
        unknownAlbumName: String,
    ): TrackActionTargets {
        val album = if (track.album.isNullOrBlank()) null
            else OfflineLibraryGrouping.albums(library, unknownAlbumName)
                .firstOrNull { track.id in it.trackIds }
        val artist = if (track.artist.isNullOrBlank()) null
            else OfflineLibraryGrouping.artists(library)
                .firstOrNull { track.id in it.trackIds }
        return TrackActionTargets(
            album?.takeUnless { current == LibraryDetail.Album(it.key) },
            artist?.takeUnless { current == LibraryDetail.Artist(it.key) },
        )
    }
}
