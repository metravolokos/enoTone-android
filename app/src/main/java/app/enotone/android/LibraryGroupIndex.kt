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
    val albumArtistAlbumCounts: Map<String, Int> = emptyMap(),
) {
    companion object {
        fun build(
            tracks: List<OfflineTrackRow>,
            unknownAlbumName: String,
            compilationsName: String = "Разные исполнители",
        ): LibraryGroupIndex {
            val albums = OfflineLibraryGrouping.albums(tracks, unknownAlbumName)
            val artists = OfflineLibraryGrouping.artists(tracks)
            val authors = OfflineLibraryGrouping.albumArtists(tracks, compilationsName)
            val albumByTrack = byTrack(albums)
            val byId = tracks.associateBy { it.id }
            val counts = authors.associate { group ->
                // Один и тот же заголовок может принадлежать разным релизам;
                // считаем точно те же ключи, что использует вкладка «Альбомы».
                val albumKeys = group.trackIds.mapNotNull { albumByTrack[it]?.key }
                    .filterNot { it == "album:missing" }.toSet()
                group.key to albumKeys.size
            }
            return LibraryGroupIndex(
                tracksById = byId, albums = albums, artists = artists,
                albumArtists = authors, albumByTrackId = albumByTrack,
                artistByTrackId = byTrack(artists), albumArtistAlbumCounts = counts,
            )
        }

        private fun byTrack(groups: List<OfflineLibraryGroup>): Map<String, OfflineLibraryGroup> =
            buildMap {
                groups.forEach { group -> group.trackIds.forEach { put(it, group) } }
            }
    }
}
