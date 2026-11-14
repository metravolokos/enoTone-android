package app.enotone.android

import app.enotone.library.LibraryText
import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineLibraryGrouping
import app.enotone.library.OfflineTrackCatalog
import app.enotone.library.OfflineTrackRow
import java.util.Locale

/** Проекция уже загруженного снапшота: не читает Room или сеть. */
internal data class ArtistAlbum(val group: OfflineLibraryGroup, val year: Int?)

internal data class ArtistDetailPlan(
    val albums: List<ArtistAlbum>,
    val songs: List<OfflineTrackRow>,
    val compilationsOnly: Boolean,
)

/**
 * В «Альбомы» попадают релизы, где исполнитель указан как Album Artist
 * (либо исполнителем трека при отсутствующем Album Artist).
 * Треки из чужих сборников остаются в «Песнях», но не выдают сборник за сольный альбом.
 */
internal object ArtistDetailPlanner {
    fun plan(
        artist: OfflineLibraryGroup,
        allTracks: List<OfflineTrackRow>,
        unknownAlbumName: String,
    ): ArtistDetailPlan {
        val members = artist.trackIds
        val byId = allTracks.associateBy { it.id }
        val tracks = allTracks.filter { it.id in members }
        if (tracks.isEmpty()) return ArtistDetailPlan(emptyList(), emptyList(), true)

        val albums = OfflineLibraryGrouping.albums(allTracks, unknownAlbumName)
            .asSequence()
            .filter { it.key != "album:missing" && it.trackIds.any(members::contains) }
            .map { group ->
                val groupTracks = group.trackIds.mapNotNull(byId::get)
                ArtistAlbum(group, OfflineTrackCatalog.commonAlbumYear(groupTracks))
            }.toList()
            .sortedWith(compareBy<ArtistAlbum> { it.year == null }
                .thenByDescending { it.year ?: Int.MIN_VALUE }
                .then(Comparator { a, b -> LibraryText.compare(a.group.name, b.group.name) })
                .thenBy { it.group.key })

        val ownAlbums = albums.filter {
            normalized(it.group.subtitle) == normalized(artist.name)
        }
        val others = albums.filterNot { it in ownAlbums }
        val ordered = buildList {
            val added = mutableSetOf<String>()
            for (album in ownAlbums + others) {
                val rows = OfflineTrackCatalog.albumOrder(tracks.filter { it.id in album.group.trackIds })
                rows.forEach { if (added.add(it.id)) add(it) }
            }
            tracks.filterNot { it.id in added }
                .sortedWith(Comparator { a, b ->
                    LibraryText.compare(a.title, b.title).takeIf { it != 0 }
                        ?: a.id.compareTo(b.id)
                }).forEach(::add)
        }
        return ArtistDetailPlan(ownAlbums, ordered, ownAlbums.isEmpty())
    }

    private fun normalized(value: String?): String? =
        value?.trim()?.lowercase(Locale.ROOT)
}
