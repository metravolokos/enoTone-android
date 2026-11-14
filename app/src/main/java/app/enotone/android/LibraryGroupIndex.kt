package app.enotone.android

import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineLibraryGrouping
import app.enotone.library.OfflineTrackRow

/** Один индекс на снимок медиатеки. Строится на io, не при открытии листа или детали. */
internal data class LibraryGroupIndex(
    val tracksById: Map<String, OfflineTrackRow> = emptyMap(),
    val albums: List<OfflineLibraryGroup> = emptyList(),
    val artists: List<OfflineLibraryGroup> = emptyList(),
    val albumArtists: List<OfflineLibraryGroup> = emptyList(),
    val albumByTrackId: Map<String, OfflineLibraryGroup> = emptyMap(),
    val artistByTrackId: Map<String, OfflineLibraryGroup> = emptyMap(),
) {
    companion object {
        fun build(tracks: List<OfflineTrackRow>, unknownAlbumName: String): LibraryGroupIndex {
            val albums = OfflineLibraryGrouping.albums(tracks, unknownAlbumName)
            val artists = OfflineLibraryGrouping.artists(tracks)
            return LibraryGroupIndex(
                tracks.associateBy { it.id }, albums, artists,
                OfflineLibraryGrouping.albumArtists(tracks),
                byTrack(albums), byTrack(artists),
            )
        }

        private fun byTrack(groups: List<OfflineLibraryGroup>): Map<String, OfflineLibraryGroup> =
            buildMap {
                groups.forEach { group -> group.trackIds.forEach { put(it, group) } }
            }
    }
}
