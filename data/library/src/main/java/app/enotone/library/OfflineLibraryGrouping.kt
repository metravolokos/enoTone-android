package app.enotone.library

import java.text.Normalizer
import java.util.Locale

/**
 * Pure, local virtual views of saved tags. The cloud directory is flat.
 * A scanned file lacking ALBUM belongs to an Unknown Album group; an
 * unindexed file does NOT fabricate a group before its tags are read.
 */
data class OfflineLibraryGroup(
    val key: String,
    val name: String,
    val subtitle: String?,
    val trackIds: Set<String>,
)

object OfflineLibraryGrouping {
    private fun normalized(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)

    private fun known(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }

    private val albumOrder = compareBy<OfflineLibraryGroup> { it.key == "album:missing" }
        .then(Comparator { a, b -> LibraryText.compare(a.name, b.name) })
        .then(Comparator { a, b ->
            LibraryText.compare(a.subtitle.orEmpty(), b.subtitle.orEmpty())
        })
        .thenBy { it.key }

    private val performerOrder = Comparator<OfflineLibraryGroup> { a, b ->
        LibraryText.compare(a.name, b.name, dropArticle = true).takeIf { it != 0 }
            ?: a.key.compareTo(b.key)
    }

    /** Prefer Album Artist for album identity, fallback to track Artist only here. */
    fun albums(
        tracks: List<OfflineTrackRow>,
        unknownAlbumName: String = "Unknown Album",
    ): List<OfflineLibraryGroup> {
        val map = linkedMapOf<String, MutableList<OfflineTrackRow>>()
        for (row in tracks) {
            val album = known(row.album)
            // The row title may be a fallback filename; don't create a fake
            // Unknown Album for thousands of files not scanned yet.
            if (album == null && !row.hasStoredTagData &&
                known(row.artist) == null && known(row.albumArtist) == null) continue
            val artist = known(row.albumArtist) ?: known(row.artist)
            // All genuinely missing album tags belong to ONE virtual album,
            // regardless of performer. Keep it distinct from a literal album
            // called "Unknown Album" and do not depend on translation.
            val key = if (album == null) "album:missing"
                else "album:" + normalized(album) + "\u0000" +
                    (artist?.let(::normalized) ?: "")
            map.getOrPut(key) { mutableListOf() }.add(row)
        }
        return map.map { (key, rows) ->
            val first = rows.first()
            OfflineLibraryGroup(
                key = key,
                name = known(first.album) ?: unknownAlbumName,
                subtitle = if (first.album == null) null
                    else known(first.albumArtist) ?: known(first.artist),
                trackIds = rows.mapTo(linkedSetOf()) { it.id },
            )
        }.sortedWith(albumOrder)
    }

    /** Track-level Artist only, independent from the Album Artist tag. */
    fun artists(tracks: List<OfflineTrackRow>): List<OfflineLibraryGroup> =
        performerGroups(tracks, "artist:", { it.artist })

    /** Album Artist only, never invent one from a track-level Artist. */
    fun albumArtists(tracks: List<OfflineTrackRow>): List<OfflineLibraryGroup> =
        performerGroups(tracks, "album-artist:", { it.albumArtist })

    private fun performerGroups(
        tracks: List<OfflineTrackRow>,
        prefix: String,
        select: (OfflineTrackRow) -> String?,
    ): List<OfflineLibraryGroup> {
        val map = linkedMapOf<String, MutableList<OfflineTrackRow>>()
        for (row in tracks) {
            val name = known(select(row)) ?: continue
            val key = prefix + normalized(name)
            map.getOrPut(key) { mutableListOf() }.add(row)
        }
        return map.map { (key, rows) ->
            OfflineLibraryGroup(
                key = key,
                name = known(select(rows.first()))!!,
                subtitle = null,
                trackIds = rows.mapTo(linkedSetOf()) { it.id },
            )
        }.sortedWith(performerOrder)
    }

    fun matches(group: OfflineLibraryGroup, query: String): Boolean {
        return LibraryText.containsAllTerms(
            listOfNotNull(group.name, group.subtitle).joinToString(" "), query,
        )
    }
}
