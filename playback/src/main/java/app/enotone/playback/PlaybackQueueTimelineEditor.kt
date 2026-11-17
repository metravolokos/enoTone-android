package app.enotone.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ShuffleOrder

/** Applies a planned queue edit to Media3 without owning the logical queue state. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackQueueTimelineEditor(private val player: ExoPlayer) {
    fun applyShuffledEdit(
        plan: QueueEditPlan,
        newIds: List<String>,
        sources: Map<String, MediaSource>,
        working: MutableList<String>,
    ) {
        newIds.forEach { id ->
            player.addMediaSources(listOf(sources.getValue(id)))
            working.add(id)
        }
        player.setShuffleOrder(
            ShuffleOrder.DefaultShuffleOrder(checkNotNull(plan.layout.shuffledIndices), System.nanoTime()))
    }

    fun applyLinearEdit(
        plan: QueueEditPlan,
        edit: QueueEdit,
        originalTrackIds: List<String>,
        sources: Map<String, MediaSource>,
        working: MutableList<String>,
    ) {
        val moved = plan.movedIds.toHashSet()
        val currentId = checkNotNull(plan.layout.currentTrackId)
        var anchor = if (edit is QueueEdit.PlayNext) currentId
            else originalTrackIds.last { it !in moved }
        plan.movedIds.forEach { id ->
            val from = working.indexOf(id)
            val to = working.indexOf(anchor) + 1
            if (from == -1) {
                player.addMediaSources(to, listOf(sources.getValue(id)))
                working.add(to, id)
            } else {
                val target = if (from < to) to - 1 else to
                if (from != target) {
                    player.moveMediaItem(from, target)
                    working.removeAt(from)
                    working.add(target, id)
                }
            }
            anchor = id
        }
    }
}
