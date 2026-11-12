package app.enotone.playback

import androidx.media3.common.Player
import kotlin.math.roundToLong

/**
 * Pure, bounded conversion for an interactive playback position slider.
 *
 * Media3 duration can be TIME_UNSET (negative). Never send a seek to an
 * unknown duration, or trust invalid slider input such as NaN/Infinity.
 */
object PlaybackSeekPolicy {
    fun isKnownDuration(durationMs: Long): Boolean = durationMs > 0L

    /** Перемотка пользователем или по команде MediaSession — повод сохранить позицию сразу. */
    fun isUserSeek(discontinuityReason: Int): Boolean =
        discontinuityReason == Player.DISCONTINUITY_REASON_SEEK ||
            discontinuityReason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT

    fun fraction(positionMs: Long, durationMs: Long): Float {
        if (!isKnownDuration(durationMs)) return 0f
        return (positionMs.toDouble() / durationMs.toDouble())
            .coerceIn(0.0, 1.0).toFloat()
    }

    fun positionForFraction(durationMs: Long, fraction: Float): Long? {
        if (!isKnownDuration(durationMs) || !fraction.isFinite()) return null
        return (durationMs.toDouble() *
            fraction.toDouble().coerceIn(0.0, 1.0))
            .roundToLong().coerceIn(0L, durationMs)
    }

    fun formatTime(positionMs: Long): String {
        if (positionMs < 0L) return "--:--"
        val totalSeconds = positionMs / 1000L
        val seconds = (totalSeconds % 60L).toString().padStart(2, '0')
        val minutes = ((totalSeconds / 60L) % 60L).toString().padStart(2, '0')
        val hours = totalSeconds / 3600L
        return if (hours > 0L) {
            "${hours.toString().padStart(2, '0')}:$minutes:$seconds"
        } else "$minutes:$seconds"
    }
}
