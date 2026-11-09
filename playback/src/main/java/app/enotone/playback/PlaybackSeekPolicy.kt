package app.enotone.playback

import kotlin.math.roundToLong

/**
 * Pure, bounded conversion for an interactive playback position slider.
 *
 * Media3 duration can be TIME_UNSET (negative). Never send a seek to an
 * unknown duration, or trust invalid slider input such as NaN/Infinity.
 */
object PlaybackSeekPolicy {
    fun isKnownDuration(durationMs: Long): Boolean = durationMs > 0L

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
        return if (hours > 0L) "$hours:$minutes:$seconds" else "$minutes:$seconds"
    }
}
