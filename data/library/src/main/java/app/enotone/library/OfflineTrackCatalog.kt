package app.enotone.library

import app.enotone.database.RemoteTrackEntity
import java.util.Locale

/**
 * Private, on-device presentation catalogue. This never makes network calls
 * and never passes WebDAV paths, source keys, ETags or account identifiers to UI.
 */
data class OfflineTrackRow(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArtist: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    /** True only for a real stored tag, not a filename-derived title. */
    val hasStoredTagData: Boolean = false,
)

object OfflineTrackCatalog {
    fun build(sourceKey: String, tracks: List<RemoteTrackEntity>): List<OfflineTrackRow> =
        tracks.asSequence()
            .filter { it.sourceKey == sourceKey && it.missingScans == 0 }
            .map { track ->
                val fallback = track.fileName.substringBeforeLast('.', track.fileName)
                    .ifBlank { track.fileName }
                OfflineTrackRow(
                    id = track.id,
                    title = track.title?.takeIf { it.isNotBlank() } ?: fallback,
                    artist = track.artist?.takeIf { it.isNotBlank() },
                    album = track.album?.takeIf { it.isNotBlank() },
                    albumArtist = track.albumArtist?.takeIf { it.isNotBlank() },
                    year = track.year,
                    trackNumber = track.trackNumber,
                    discNumber = track.discNumber,
                    hasStoredTagData = listOf(
                        track.title, track.artist, track.album, track.albumArtist,
                    ).any { !it.isNullOrBlank() } ||
                        track.year != null || track.trackNumber != null ||
                        track.discNumber != null,
                )
            }
            .sortedWith(compareBy<OfflineTrackRow> { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.id })
            .toList()

    /**
     * Album-specific order: disc then track number. Files without such tags
     * remain readable at the end of each disc in stable title order.
     * No network, no invented metadata and no mutation of the saved catalogue.
     */
    fun albumOrder(items: List<OfflineTrackRow>): List<OfflineTrackRow> =
        items.sortedWith(compareBy<OfflineTrackRow> { it.discNumber ?: 1 }
            .thenBy { it.trackNumber ?: Int.MAX_VALUE }
            .thenBy { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.id })

    /** A compilation with conflicting release years must not show a fake one. */
    fun commonAlbumYear(items: List<OfflineTrackRow>): Int? =
        items.mapNotNull { it.year }.distinct().singleOrNull()

    fun search(items: List<OfflineTrackRow>, query: String): List<OfflineTrackRow> {
        val terms = query.trim().lowercase(Locale.ROOT)
            .split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return items
        return items.filter { row ->
            val haystack = listOfNotNull(row.title, row.artist, row.album, row.albumArtist)
                .joinToString(" ").lowercase(Locale.ROOT)
            terms.all { haystack.contains(it) }
        }
    }
}
