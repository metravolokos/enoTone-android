package app.enotone.playback

/**
 * Last known duration of the current media item.
 *
 * Media3 can temporarily report TIME_UNSET while a progressive source is being
 * prepared or re-prepared. The UI must not mistake that transient value for a
 * permanently unseekable track, but a duration from another item must never leak
 * into the current slider or checkpoint.
 */
internal class PlaybackDurationMemory {
    private var known: Pair<String, Long>? = null

    fun resolve(mediaId: String?, rawDurationMs: Long): Long {
        if (mediaId != null && PlaybackSeekPolicy.isKnownDuration(rawDurationMs)) {
            known = mediaId to rawDurationMs
            return rawDurationMs
        }
        return known?.takeIf { it.first == mediaId }?.second ?: 0L
    }

    fun remember(mediaId: String, durationMs: Long) {
        if (PlaybackSeekPolicy.isKnownDuration(durationMs)) known = mediaId to durationMs
    }

    fun clear() {
        known = null
    }
}
