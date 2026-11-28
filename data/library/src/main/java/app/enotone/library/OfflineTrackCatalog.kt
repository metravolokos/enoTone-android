package app.enotone.library

import app.enotone.database.RemoteTrackEntity
import java.time.DateTimeException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
    val genre: String? = null,
    val durationMs: Long? = null,
    /** True only for a real stored tag, not a filename-derived title. */
    val hasStoredTagData: Boolean = false,
    /** Null means the row predates Room v5 or belongs to the first import. */
    val firstSeenEpochMs: Long? = null,
    /**
     * Дата файла с сервера (`Last-Modified`) как порядок показа для «Недавно добавленных»,
     * когда [firstSeenEpochMs] нет (первый импорт аккаунта, строки до Room v5). Число, не путь:
     * утечки приватных данных не добавляет. `null`, если сервер не отдал дату или она не разобрана.
     */
    val sourceModifiedEpochMs: Long? = null,
) {
    /** Precomputed once per immutable row, including a copy() with changed metadata. */
    val normalizedSearchKey: String =
        LibraryText.searchKey(title, artist, album, albumArtist, genre)
}

object OfflineTrackCatalog {
    private val titleOrder = Comparator<OfflineTrackRow> { left, right ->
        LibraryText.compare(left.title, right.title).takeIf { it != 0 }
            ?: left.id.compareTo(right.id)
    }

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
                    genre = track.genre?.takeIf { it.isNotBlank() },
                    durationMs = track.durationMs,
                    firstSeenEpochMs = track.firstSeenEpochMs,
                    sourceModifiedEpochMs = parseHttpDate(track.remoteModified),
                    hasStoredTagData = listOf(
                        track.title, track.artist, track.album, track.albumArtist,
                    ).any { !it.isNullOrBlank() } ||
                        track.year != null || track.trackNumber != null ||
                        track.discNumber != null || track.genre != null,
                )
            }
            .sortedWith(titleOrder)
            .toList()

    /**
     * Album-specific order: disc then track number. Files without such tags
     * remain readable at the end of each disc in stable title order.
     * No network, no invented metadata and no mutation of the saved catalogue.
     */
    fun albumOrder(items: List<OfflineTrackRow>): List<OfflineTrackRow> =
        items.sortedWith(compareBy<OfflineTrackRow> { it.discNumber ?: 1 }
            .thenBy { it.trackNumber ?: Int.MAX_VALUE }
            .then(titleOrder))

    /** A compilation with conflicting release years must not show a fake one. */
    fun commonAlbumYear(items: List<OfflineTrackRow>): Int? =
        items.mapNotNull { it.year }.distinct().singleOrNull()

    /** Only return a single unambiguous genre for a whole album. */
    fun commonAlbumGenre(items: List<OfflineTrackRow>): String? =
        items.mapNotNull { it.genre?.takeIf(String::isNotBlank) }
            .distinctBy { it.lowercase(Locale.ROOT) }.singleOrNull()

    fun search(items: List<OfflineTrackRow>, query: String): List<OfflineTrackRow> =
        search(items, LibraryText.searchTerms(query))

    fun search(items: List<OfflineTrackRow>, terms: List<String>): List<OfflineTrackRow> =
        if (terms.isEmpty()) items
        else items.filter { LibraryText.matchesAllTerms(it.normalizedSearchKey, terms) }

    /**
     * `Last-Modified` в формате HTTP (RFC 1123, `Sun, 08 Nov 2026 08:00:00 GMT`).
     * Неразобранная или пустая дата — `null`: чужой формат сервера не должен ронять каталог.
     */
    private fun parseHttpDate(raw: String?): Long? = try {
        raw?.takeIf { it.isNotBlank() }
            ?.let { ZonedDateTime.parse(it.trim(), DateTimeFormatter.RFC_1123_DATE_TIME) }
            ?.toInstant()
            ?.toEpochMilli()
    } catch (_: DateTimeException) {
        null
    }
}
