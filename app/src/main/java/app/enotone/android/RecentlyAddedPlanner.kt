package app.enotone.android

import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineTrackRow

/** Выборка D4c использует только строки текущего локального снимка медиатеки. */
internal object RecentlyAddedPlanner {
    /**
     * Порядок «недавних»: дата, когда файл впервые учтён ([OfflineTrackRow.firstSeenEpochMs]),
     * либо, если её нет (первый импорт аккаунта, строки до Room v5), дата файла с сервера
     * ([OfflineTrackRow.sourceModifiedEpochMs]). Обе `null` — строка не участвует.
     */
    private fun orderDate(row: OfflineTrackRow): Long? =
        row.firstSeenEpochMs ?: row.sourceModifiedEpochMs

    fun tracks(
        tracks: List<OfflineTrackRow>,
        limit: Int = ALL_TRACKS_LIMIT,
    ): List<OfflineTrackRow> = tracks.asSequence()
        .mapNotNull { row -> orderDate(row)?.let { row to it } }
        .sortedWith(
            compareByDescending<Pair<OfflineTrackRow, Long>> { it.second }
                .thenBy { it.first.id },
        )
        .take(limit.coerceAtLeast(0))
        .map { it.first }
        .toList()

    fun albums(
        groups: List<OfflineLibraryGroup>,
        tracksById: Map<String, OfflineTrackRow>,
        limit: Int = ALL_ALBUMS_LIMIT,
    ): List<OfflineLibraryGroup> = groups.asSequence()
        .filterNot { it.key == UNKNOWN_ALBUM_KEY }
        .mapNotNull { group ->
            val latestDate = group.trackIds.asSequence()
                .mapNotNull { tracksById[it]?.let(::orderDate) }
                .maxOrNull()
                ?: return@mapNotNull null
            group to latestDate
        }
        .sortedWith(
            compareByDescending<Pair<OfflineLibraryGroup, Long>> { it.second }
                .thenBy { it.first.key },
        )
        .take(limit.coerceAtLeast(0))
        .map { it.first }
        .toList()

    const val HOME_ALBUMS_LIMIT = 10
    const val HOME_TRACKS_LIMIT = 5
    const val ALL_ALBUMS_LIMIT = 50
    const val ALL_TRACKS_LIMIT = 100

    private const val UNKNOWN_ALBUM_KEY = "album:missing"
}
