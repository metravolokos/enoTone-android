package app.enotone.playback

import java.security.MessageDigest

/**
 * Bound only the selected track's background copy. Playback itself remains
 * available through the established HTTPS WebDAV DataSource if this is skipped.
 */
object TransientCachePolicy {
    const val MAX_CURRENT_BYTES: Long = 256L * 1024L * 1024L
    const val MAX_DISK_BYTES: Long = 512L * 1024L * 1024L
    const val MIN_FREE_AFTER_DOWNLOAD: Long = 128L * 1024L * 1024L

    fun canDownload(fileBytes: Long, availableBytes: Long): Boolean =
        fileBytes > 0 && fileBytes <= MAX_CURRENT_BYTES &&
            availableBytes >= fileBytes + MIN_FREE_AFTER_DOWNLOAD

    /**
     * A prefetch may start only while both tickets are still current. `cancelNext()` bumps
     * `nextGeneration` exactly to invalidate an in-flight candidate, so a worker that already
     * passed this check must pass it again after publishing its writer — otherwise a cancel
     * landing in that window finds nothing to cancel and the download runs anyway.
     */
    fun ticketStillValid(
        closed: Boolean,
        generation: Long,
        currentTicket: Long,
        nextGeneration: Long,
        nextTicket: Long,
    ): Boolean = !closed && generation == currentTicket && nextGeneration == nextTicket

    /** Opaque cache key; no filename, URI, password or headers in its index. */
    fun key(track: QueueTrack): String {
        val value = listOf(track.id, track.remoteSize.toString(),
            track.remoteEtag.orEmpty()).joinToString("\u0000")
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
