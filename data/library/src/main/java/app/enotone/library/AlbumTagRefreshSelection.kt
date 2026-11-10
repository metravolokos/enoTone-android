package app.enotone.library

/**
 * Screen-session retry ordering for user-confirmed album refreshes.
 *
 * Completed IDs are skipped. Failed attempts remain eligible, but untried
 * tracks go first so a flaky first file cannot starve the rest of the album.
 * The input is an already validated list of local playable MP3/FLAC IDs from
 * precisely one selected album; this pure function never touches Room/WebDAV.
 */
object AlbumTagRefreshSelection {
    fun nextBatch(
        albumIds: List<String>,
        completedIds: Set<String>,
        attemptedIds: Set<String>,
        limit: Int = AlbumTagRefresher.MAX_FILES_PER_TAP,
    ): List<String> {
        require(limit in 1..AlbumTagRefresher.MAX_FILES_PER_TAP)
        val waiting = albumIds.distinct().filterNot { it in completedIds }
        val fresh = waiting.filterNot { it in attemptedIds }
        val retried = waiting.filter { it in attemptedIds }
        return (fresh + retried).take(limit)
    }
}
