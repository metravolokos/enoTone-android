package app.enotone.playback

import java.io.IOException

/** Checked numbers only; no logging of server headers or user filenames. */
internal object WebDavPlaybackRange {
    private val pattern = Regex(
        """bytes(?:\s+|=)(\d+)-(\d+)/(\d+|\*)""",
        RegexOption.IGNORE_CASE,
    )

    fun count(header: String?, start: Long, end: Long, knownTotal: Long): Long {
        if (knownTotal <= 0 || start < 0 || end < start || end >= knownTotal) {
            throw IOException("Invalid playback range")
        }
        val groups = header?.trim()?.let { pattern.matchEntire(it) }
            ?: throw IOException("Invalid playback range")
        val actualStart = groups.groupValues[1].toLongOrNull()
        val actualEnd = groups.groupValues[2].toLongOrNull()
        val totalString = groups.groupValues[3]
        val actualTotal = if (totalString == "*") null else totalString.toLongOrNull()
        if (actualStart != start || actualEnd != end ||
            (totalString != "*" && actualTotal != knownTotal)) {
            throw IOException("Invalid playback range")
        }
        return end - start + 1
    }
}
