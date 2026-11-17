package app.enotone.playback

/** Immutable queue view in actual playback order; currentIndex is zero-based. */
data class PlaybackQueueSnapshot(
    val trackIds: List<String>,
    val currentIndex: Int,
) {
    init {
        require(trackIds.isNotEmpty() && trackIds.size <= PlaybackQueue.MAX_TRACKS)
        // The playback owner builds this from the validated queue and caches the
        // list. Rechecking uniqueness here would allocate a full set every poll.
        require(currentIndex >= 0 && currentIndex < trackIds.size)
    }
}
