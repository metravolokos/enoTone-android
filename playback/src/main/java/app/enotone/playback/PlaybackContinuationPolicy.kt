package app.enotone.playback

/**
 * A defensive recovery of an unexpected Media3 ENDED while a real next item
 * exists. Normal automatic transitions do not need this; Pause and audio
 * focus suppression must never be turned into unsolicited playback.
 *
 * These inputs contain no media IDs, paths, or personal information.
 */
object PlaybackContinuationPolicy {
    fun shouldAutoRecover(
        ended: Boolean,
        playWhenReady: Boolean,
        hasNext: Boolean,
        repeatOne: Boolean,
        unsuppressed: Boolean,
    ): Boolean = ended && playWhenReady && hasNext && !repeatOne && unsuppressed
}
