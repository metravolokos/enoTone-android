package app.enotone.playback

import java.io.IOException

/** Fixed diagnostic codes only. Never keep exception text, URLs or headers. */
enum class PlaybackDataIssue {
    OPEN_NETWORK,
    OPEN_STATUS,
    OPEN_RANGE,
    OPEN_ENCODING,
    OPEN_VERSION,
    OPEN_LENGTH,
    READ_NETWORK,
    READ_TRUNCATED,
    UNKNOWN,
}

internal class PlaybackDataIssueException(
    val issue: PlaybackDataIssue,
) : IOException("Playback data issue")

/** Extract only our fixed numeric-free code through Media3's exception chain. */
object PlaybackDataIssueClassifier {
    fun classify(error: Throwable): PlaybackDataIssue {
        var current: Throwable? = error
        repeat(12) {
            if (current is PlaybackDataIssueException) {
                return (current as PlaybackDataIssueException).issue
            }
            current = current?.cause ?: return PlaybackDataIssue.UNKNOWN
        }
        return PlaybackDataIssue.UNKNOWN
    }
}
