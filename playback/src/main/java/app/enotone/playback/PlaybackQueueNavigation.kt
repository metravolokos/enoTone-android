package app.enotone.playback

/** Cached display order and deferred selection for the logical queue. */
internal class PlaybackQueueNavigation {
    private var trackIds: List<String> = emptyList()
    private var trackIndices: Map<String, Int> = emptyMap()
    private var pendingTrackId: String? = null

    val hasPendingSeek: Boolean get() = pendingTrackId != null

    fun updateOrder(
        originalTrackIds: List<String>,
        originalTrackIndices: Map<String, Int>,
        configuredShuffle: IntArray?,
        shuffleEnabled: Boolean,
    ) {
        if (!shuffleEnabled || configuredShuffle == null ||
            configuredShuffle.size != originalTrackIds.size
        ) {
            trackIds = originalTrackIds
            trackIndices = originalTrackIndices
            return
        }
        val order = configuredShuffle.map { originalTrackIds[it] }
        trackIds = order
        trackIndices = order.withIndex().associate { (index, id) -> id to index }
    }

    fun positionOf(trackId: String): Int? = trackIndices[trackId]

    fun snapshot(currentTrackId: String?): PlaybackQueueSnapshot? = currentTrackId?.let { id ->
        val currentIndex = trackIndices[id]
        if (currentIndex == null || trackIds.isEmpty()) null
        else PlaybackQueueSnapshot(trackIds, currentIndex)
    }

    fun selectPosition(
        position: Int,
        currentTrackId: String?,
        seekLoadedTarget: (String) -> Boolean,
        stageMoreItems: () -> Boolean,
    ): Boolean = trackIds.getOrNull(position)?.let { targetId ->
        if (targetId == currentTrackId) {
            pendingTrackId = null
            true
        } else {
            pendingTrackId = targetId
            if (seekLoadedTarget(targetId)) {
                pendingTrackId = null
                true
            } else if (stageMoreItems()) {
                true
            } else {
                pendingTrackId = null
                false
            }
        }
    } ?: false

    fun applyPendingSeek(seekLoadedTarget: (String) -> Boolean): Boolean =
        pendingTrackId?.let { targetId ->
            if (seekLoadedTarget(targetId)) {
                pendingTrackId = null
                true
            } else {
                false
            }
        } ?: false

    fun clearPendingSeek() {
        pendingTrackId = null
    }

    fun clear() {
        trackIds = emptyList()
        trackIndices = emptyMap()
        pendingTrackId = null
    }
}
