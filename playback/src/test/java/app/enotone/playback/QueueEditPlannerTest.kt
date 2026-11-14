package app.enotone.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueEditPlannerTest {
    private fun id(n: Int) = n.toString(16).padStart(64, '0')
    private fun layout(
        current: Int = 2,
        shuffle: IntArray? = null,
    ) = QueueLayout((1..4).map(::id), shuffle, id(current))

    @Test fun nextInOrdinaryQueueMovesExistingTrackAfterCurrent() {
        val result = QueueEditPlanner.plan(layout(), QueueEdit.PlayNext(listOf(id(1))))
        assertEquals(listOf(id(2), id(3), id(1), id(4)), result.layout.playbackIds())
        assertEquals(id(3), result.layout.currentTrackId)
        assertEquals(listOf(id(1)), result.movedIds)
        assertEquals(QueueEditStatus.APPLIED, result.result.status)
    }

    @Test fun nextMultipleTracksMaintainsSelectionOrderAndDeduplicates() {
        val result = QueueEditPlanner.plan(
            layout(), QueueEdit.PlayNext(listOf(id(4), id(1), id(4), id(5))))
        assertEquals(listOf(id(2), id(3), id(4), id(1), id(5)),
            result.layout.playbackIds())
        assertEquals(3, result.result.count)
        assertEquals(5, result.layout.trackIds.size)
    }

    @Test fun appendMovesExistingToTrueEndAndAddsNewInOrder() {
        val result = QueueEditPlanner.plan(
            layout(), QueueEdit.Append(listOf(id(1), id(5), id(6))))
        assertEquals(listOf(id(2), id(3), id(4), id(1), id(5), id(6)),
            result.layout.playbackIds())
    }

    @Test fun shuffledNextIsImmediatelyAfterCurrentInPlaybackTraversal() {
        val shuffled = layout(shuffle = intArrayOf(2, 0, 3, 1))
        val result = QueueEditPlanner.plan(shuffled, QueueEdit.PlayNext(listOf(id(4), id(5))))
        assertEquals(listOf(id(3), id(4), id(5), id(1), id(2)),
            result.layout.playbackIds())
        assertEquals(listOf(id(1), id(2), id(3), id(4), id(5)), result.layout.trackIds)
        assertArrayEquals(intArrayOf(2, 3, 4, 0, 1), result.layout.shuffledIndices)
        assertEquals(id(3), result.layout.currentTrackId)
    }

    @Test fun shuffledAppendDoesNotRedistributeNewSongs() {
        val shuffled = layout(shuffle = intArrayOf(2, 0, 3, 1))
        val result = QueueEditPlanner.plan(shuffled, QueueEdit.Append(listOf(id(1), id(5))))
        assertEquals(listOf(id(3), id(4), id(2), id(1), id(5)),
            result.layout.playbackIds())
        assertEquals(5, result.layout.trackIds.size)
        assertEquals(5, result.layout.shuffledIndices!!.toSet().size)
    }

    @Test fun currentSongIsNeverMovedOrDuplicated() {
        val only = QueueEditPlanner.plan(layout(), QueueEdit.PlayNext(listOf(id(3), id(3))))
        assertEquals(QueueEditStatus.ALREADY_PLAYING, only.result.status)
        assertEquals(0, only.result.count)
        val combined = QueueEditPlanner.plan(
            layout(), QueueEdit.PlayNext(listOf(id(3), id(4), id(3))))
        assertEquals(QueueEditStatus.APPLIED, combined.result.status)
        assertEquals(1, combined.result.count)
        assertEquals(4, combined.layout.trackIds.distinct().size)
        assertEquals(id(3), combined.layout.currentTrackId)
    }

    @Test fun emptyQueueStartsWithSelectionAsNewQueue() {
        val result = QueueEditPlanner.plan(
            QueueLayout(emptyList(), null, null), QueueEdit.Append(listOf(id(2), id(1))))
        assertEquals(listOf(id(2), id(1)), result.layout.playbackIds())
        assertEquals(id(2), result.layout.currentTrackId)
        assertEquals(2, result.result.count)
        assertEquals(QueueEditStatus.APPLIED, result.result.status)
    }

    @Test fun maxCapacityRejectsExtrasButCanMoveExisting() {
        val full = QueueLayout((1..PlaybackQueue.MAX_TRACKS).map(::id), null, id(100))
        val result = QueueEditPlanner.plan(
            full, QueueEdit.PlayNext(listOf(id(99), id(10_001), id(10_002))))
        assertEquals(QueueEditStatus.QUEUE_FULL, result.result.status)
        assertEquals(PlaybackQueue.MAX_TRACKS, result.layout.trackIds.size)
        assertEquals(listOf(id(100), id(99)), result.layout.playbackIds().drop(98).take(2))
        assertFalse(id(10_001) in result.layout.trackIds)
        assertEquals(1, result.result.count)
    }

    @Test fun alreadyFullWithOnlyNewItemsDoesNotChangeLayout() {
        val full = QueueLayout((1..PlaybackQueue.MAX_TRACKS).map(::id), null, id(1))
        val result = QueueEditPlanner.plan(full, QueueEdit.Append(listOf(id(10_001))))
        assertEquals(QueueEditStatus.QUEUE_FULL, result.result.status)
        assertTrue(result.movedIds.isEmpty())
        assertEquals(full.trackIds, result.layout.trackIds)
    }

    @Test fun emptySelectionHasNoEffect() {
        val result = QueueEditPlanner.plan(layout(), QueueEdit.Append(listOf("", "")))
        assertEquals(QueueEditStatus.EMPTY_SELECTION, result.result.status)
        assertEquals(0, result.result.count)
    }

    @Test fun manuallyEditedSourceIsAccountBoundAndBackwardCompatible() {
        val account = id(77)
        assertTrue(QueueEditSource.belongsTo(account, account))
        val edited = QueueEditSource.edited(account)
        assertTrue(QueueEditSource.isValid(edited))
        assertEquals(edited, QueueEditSource.edited(edited))
        assertTrue(QueueEditSource.belongsTo(edited, account))
        assertFalse(QueueEditSource.belongsTo(edited, id(78)))
        assertFalse(QueueEditSource.isValid("edited:example@invalid.test"))
    }
}
