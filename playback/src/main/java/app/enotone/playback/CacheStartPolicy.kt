package app.enotone.playback

import androidx.media3.common.Player

/**
 * The first audible buffer always gets priority over a full-track cache GET.
 * READY means playback has enough bytes to begin. Pause must not veto caching.
 */
internal object CacheStartPolicy {
    const val START_DELAY_MS = 900L

    fun mayStart(
        playbackState: Int,
        currentMediaId: String?,
        pendingMediaId: String?,
    ): Boolean = playbackState == Player.STATE_READY &&
        pendingMediaId != null && currentMediaId == pendingMediaId
}
