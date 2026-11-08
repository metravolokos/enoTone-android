package app.enotone.playback

/**
 * Allow at most one eligible next item. The caller resolves nextMediaItemIndex
 * from the real Media3 timeline (with shuffle/repeat), never from Room order.
 */
internal object NextPrefetchSelection {
    fun select(
        currentId: String,
        nextId: String?,
        candidates: Map<String, QueueTrack>,
    ): QueueTrack? {
        if (nextId == null || nextId == currentId) return null
        return candidates[nextId]?.takeIf { it.remoteSize > 0L }
    }
}
