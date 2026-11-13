package app.enotone.android

import app.enotone.library.OfflineLibraryGrouping
import app.enotone.library.OfflineTrackCatalog
import app.enotone.library.OfflineTrackRow

/**
 * One visual cover identity per real album, regardless of the list page.
 * A virtual "Unknown Album" is NOT a real shared album: its unrelated tracks
 * retain independent picture identities. Everything is built from local tags.
 */
data class AlbumArtworkGroup(
    val key: String,
    val targets: List<PlayingArtworkTarget>,
)

data class AlbumArtworkCatalog(
    val groups: List<AlbumArtworkGroup>,
    val byGroupKey: Map<String, AlbumArtworkGroup>,
    val byTrackId: Map<String, AlbumArtworkGroup>,
) {
    companion object {
        fun build(
            tracks: List<OfflineTrackRow>,
            targetForId: (String) -> PlayingArtworkTarget?,
        ): AlbumArtworkCatalog {
            val playable = tracks.associateBy { it.id }
            val result = mutableListOf<AlbumArtworkGroup>()
            val byTrack = LinkedHashMap<String, AlbumArtworkGroup>()
            val albumGroups = OfflineLibraryGrouping.albums(tracks)
            for (group in albumGroups) {
                if (group.key == "album:missing") continue
                // First by disc/track number; fallback to another member only
                // when that embedded cover is definitively absent.
                val candidates = OfflineTrackCatalog.albumOrder(
                    group.trackIds.mapNotNull { playable[it] },
                ).mapNotNull { targetForId(it.id) }
                if (candidates.isEmpty()) continue
                val artworkGroup = AlbumArtworkGroup(group.key, candidates)
                result += artworkGroup
                group.trackIds.forEach { byTrack[it] = artworkGroup }
            }
            // No invented common cover for missing-album rows. Unindexed
            // tracks also get single-track identities, rather than being
            // hidden from progress / on-demand cover loading.
            for (track in tracks) {
                if (track.id in byTrack) continue
                val target = targetForId(track.id) ?: continue
                val single = AlbumArtworkGroup("track:" + track.id, listOf(target))
                result += single
                byTrack[track.id] = single
            }
            return AlbumArtworkCatalog(
                groups = result,
                byGroupKey = result.associateBy { it.key },
                byTrackId = byTrack,
            )
        }
    }
}
