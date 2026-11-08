package app.enotone.playback

/**
 * In-memory queue in the order of the user's visible (possibly filtered) list.
 * Nothing is persisted, enumerated over WebDAV or downloaded by this model.
 */
data class QueueTrack(
    val id: String,
    val title: String,
    val encodedPath: String,
    val remoteSize: Long,
    val remoteEtag: String?,
)

class PlaybackQueue private constructor(
    private val tracks: List<QueueTrack>,
    private var index: Int,
) {
    val items: List<QueueTrack> get() = tracks
    val size: Int get() = tracks.size
    val position: Int get() = index
    val current: QueueTrack get() = tracks[index]
    val hasNext: Boolean get() = index + 1 < tracks.size
    val hasPrevious: Boolean get() = index > 0

    fun next(): QueueTrack? {
        if (!hasNext) return null
        index++
        return current
    }

    fun previous(): QueueTrack? {
        if (!hasPrevious) return null
        index--
        return current
    }

    companion object {
        // Max library scope is an explicit upper bound, not an automatic GET budget.
        const val MAX_TRACKS = 10_000

        fun create(tracks: List<QueueTrack>, selectedId: String): PlaybackQueue {
            require(tracks.isNotEmpty() && tracks.size <= MAX_TRACKS) {
                "Invalid queue size"
            }
            require(tracks.all {
                it.id.isNotBlank() && it.title.isNotBlank() && it.remoteSize > 0L
            }) { "Invalid queue entry" }
            require(tracks.map { it.id }.toSet().size == tracks.size) {
                "Duplicate queue ID"
            }
            val at = tracks.indexOfFirst { it.id == selectedId }
            require(at >= 0) { "Selected track must be present" }
            return PlaybackQueue(tracks.toList(), at)
        }
    }
}
