package app.enotone.library

import java.text.Normalizer
import java.util.Locale

/**
 * Pure, local virtual views of saved audio tags. No folder-based inference:
 * /AllMusic is flat, and unscanned files stay in Tracks rather than forming a
 * misleading enormous "Unknown album". No network or Room access here.
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

    /** Prefer the album-artist tag so compilations remain one virtual album. */
    fun albums(tracks: List<OfflineTrackRow>): List<OfflineLibraryGroup> {
        val map = linkedMapOf<String, MutableList<OfflineTrackRow>>()
        for (row in tracks) {
            val album = known(row.album) ?: continue
            val artist = known(row.albumArtist) ?: known(row.artist)
            val key = "album:" + normalized(album) + "\u0000" +
                (artist?.let(::normalized) ?: "")
            map.getOrPut(key) { mutableListOf() }.add(row)
        }
        return map.map { (key, rows) ->
            val first = rows.first()
            OfflineLibraryGroup(
                key = key,
                name = known(first.album)!!,
                subtitle = known(first.albumArtist) ?: known(first.artist),
                trackIds = rows.mapTo(linkedSetOf()) { it.id },
            )
        }.sortedWith(compareBy<OfflineLibraryGroup> { normalized(it.name) }
            .thenBy { normalized(it.subtitle.orEmpty()) }
            .thenBy { it.key })
    }

    fun artists(tracks: List<OfflineTrackRow>): List<OfflineLibraryGroup> {
        val map = linkedMapOf<String, MutableList<OfflineTrackRow>>()
        for (row in tracks) {
            val artist = known(row.artist) ?: known(row.albumArtist) ?: continue
            val key = "artist:" + normalized(artist)
            map.getOrPut(key) { mutableListOf() }.add(row)
        }
        return map.map { (key, rows) ->
            OfflineLibraryGroup(
                key = key,
                name = known(rows.first().artist) ?: known(rows.first().albumArtist)!!,
                subtitle = null,
                trackIds = rows.mapTo(linkedSetOf()) { it.id },
            )
        }.sortedWith(compareBy<OfflineLibraryGroup> { normalized(it.name) }
            .thenBy { it.key })
    }

    fun matches(group: OfflineLibraryGroup, query: String): Boolean {
        val terms = normalized(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return true
        val text = normalized(listOfNotNull(group.name, group.subtitle).joinToString(" "))
        return terms.all { text.contains(it) }
    }
}
