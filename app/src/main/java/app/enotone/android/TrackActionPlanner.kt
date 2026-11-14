package app.enotone.android

import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineTrackRow

/** Группы из тех же тегов и тем же алгоритмом, что вкладки «Альбомы» и «Артисты». */
internal data class TrackActionTargets(
    val album: OfflineLibraryGroup?,
    val artist: OfflineLibraryGroup?,
)

internal object TrackActionPlanner {
    fun targets(
        track: OfflineTrackRow,
        index: LibraryGroupIndex,
        current: LibraryDetail?,
    ): TrackActionTargets {
        val album = if (track.album.isNullOrBlank()) null
            else index.albumByTrackId[track.id]
        val artist = if (track.artist.isNullOrBlank()) null
            else index.artistByTrackId[track.id]
        return TrackActionTargets(
            album?.takeUnless { current == LibraryDetail.Album(it.key) },
            artist?.takeUnless { current == LibraryDetail.Artist(it.key) },
        )
    }
}
