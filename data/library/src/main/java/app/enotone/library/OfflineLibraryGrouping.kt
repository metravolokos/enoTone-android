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
) {
    /** Prepared when groups are built for the loaded catalogue, never per keystroke. */
    val normalizedSearchKey: String =
        LibraryText.searchKey(listOfNotNull(name, subtitle).joinToString(" "))
}

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

    /** Stable synthetic group for albums explicitly marked as compilations. */
    const val COMPILATIONS_KEY = "album-artist:compilations"

    private val compilationNames = setOf(
        "various artists", "va", "разные исполнители", "сборник",
    )

    /**
     * Только явный Album Artist. Нельзя выводить сборник из общего названия альбома:
     * разные сольные релизы (и варианты Artist feat.) могут иметь одинаковое имя.
     */
    fun compilationTrackIds(tracks: List<OfflineTrackRow>): Set<String> =
        tracks.filterTo(mutableListOf()) { row ->
            known(row.albumArtist)?.let(::normalized)?.let(compilationNames::contains) == true
        }.mapTo(linkedSetOf()) { it.id }

    /** Album Artist groups plus one synthetic group for compilations, always first. */
    fun albumArtists(
        tracks: List<OfflineTrackRow>,
        compilationsName: String = "Разные исполнители",
    ): List<OfflineLibraryGroup> {
        val compilationIds = compilationTrackIds(tracks)
        val regular = performerGroups(tracks.filterNot { it.id in compilationIds },
            "album-artist:", { it.albumArtist })
        return if (compilationIds.isEmpty()) regular else listOf(
            OfflineLibraryGroup(COMPILATIONS_KEY, compilationsName, null, compilationIds),
        ) + regular
    }

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

    fun matches(group: OfflineLibraryGroup, query: String): Boolean =
        matches(group, LibraryText.searchTerms(query))

    fun matches(group: OfflineLibraryGroup, terms: List<String>): Boolean =
        LibraryText.matchesAllTerms(group.normalizedSearchKey, terms)
}
