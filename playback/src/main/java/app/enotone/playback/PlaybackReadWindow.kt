package app.enotone.playback

/**
 * A pure model of the Media3 DataSource boundary contract. A requested byte
 * position exactly at EOF is legal and must not trigger an HTTP Range request.
 * All values are numeric; no URLs, names, credentials or ETags.
 */
internal object PlaybackReadWindow {
    const val UNKNOWN_LENGTH: Long = -1L

    data class Window(
        val bytesAvailable: Long,
        val endInclusive: Long?,
        val openReturnLength: Long,
    )

    /** Null means position beyond EOF; the DataSource raises IO 2008. */
    fun resolve(size: Long, position: Long, requestedLength: Long): Window? {
        require(size > 0L && position >= 0L &&
            (requestedLength == UNKNOWN_LENGTH || requestedLength > 0L)) {
            "Invalid playback read numbers"
        }
        if (position > size) return null
        if (position == size) {
            return Window(
                bytesAvailable = 0L,
                endInclusive = null,
                openReturnLength = if (requestedLength == UNKNOWN_LENGTH) 0L
                    else requestedLength,
            )
        }
        val available = size - position
        val bytes = if (requestedLength == UNKNOWN_LENGTH) available
            else minOf(available, requestedLength)
        return Window(
            bytesAvailable = bytes,
            endInclusive = position + bytes - 1L,
            openReturnLength = if (requestedLength == UNKNOWN_LENGTH) bytes
                else requestedLength,
        )
    }
}
