package app.enotone.playback

import org.junit.Assert.*
import org.junit.Test

class PersistedQueueStateTest {
    private fun id(n: Int) = n.toString(16).padStart(64, '0')
    private val revision = "01234567-89ab-cdef-0123-456789abcdef"
    private fun state(
        ids: List<String> = listOf(id(1), id(2), id(3), id(4)),
        selected: String = id(3),
        shuffled: IntArray? = intArrayOf(2, 0, 3, 1),
    ) = PersistedQueueState(
        trackIds = ids,
        selectedTrackId = selected,
        positionMs = 12_345L,
        repeatMode = 2,
        shuffleEnabled = shuffled != null,
        shuffledIndices = shuffled,
        sourceKey = id(99),
        savedAtEpochMs = 12345678L,
        queueRevision = revision,
    )

    private fun roundTrip(s: PersistedQueueState): PersistedQueueState? =
        QueueStateCodec.assemble(
            QueueStateCodec.encodeLayout(s.layout()),
            QueueStateCodec.encodePosition(s.position()),
        )

    @Test fun layoutAndPositionRoundTripPreservesExactShuffleOrder() {
        val initial = state()
        val restored = roundTrip(initial)!!
        assertEquals(initial.trackIds, restored.trackIds)
        assertEquals(initial.selectedTrackId, restored.selectedTrackId)
        assertEquals(initial.positionMs, restored.positionMs)
        assertEquals(initial.repeatMode, restored.repeatMode)
        assertArrayEquals(initial.shuffledIndices, restored.shuffledIndices)
        assertEquals(initial.sourceKey, restored.sourceKey)
        assertEquals(initial.queueRevision, restored.queueRevision)
    }

    @Test fun noShuffleRoundTripHasNoPermutation() {
        val restored = roundTrip(state(shuffled = null))!!
        assertNull(restored.shuffledIndices)
        assertFalse(restored.shuffleEnabled)
    }

    @Test fun emptyDamagedAndForeignLayoutCannotBeRestored() {
        val q = QueueStateCodec.encodeLayout(state().layout())
        val p = QueueStateCodec.encodePosition(state().position())
        assertNull(QueueStateCodec.assemble("", p))
        assertNull(QueueStateCodec.assemble("{not-json", p))
        assertNull(QueueStateCodec.assemble(q.dropLast(8), p))
        assertNull(QueueStateCodec.assemble(q.replace("\"version\":1", "\"version\":7"), p))
        assertNull(QueueStateCodec.assemble(q.replace("\"trackIds\"", "\"path\""), p))
    }

    @Test fun mismatchedRevisionsAreRejectedInsteadOfCombiningTwoQueues() {
        val q = QueueStateCodec.encodeLayout(state().layout())
        val other = state().position().copy(
            queueRevision = "11111111-2222-3333-4444-555555555555",
        )
        assertNull(QueueStateCodec.assemble(q, QueueStateCodec.encodePosition(other)))
    }

    @Test fun malformedPositionDefaultsToFirstTrackZeroAndRepeatOff() {
        val q = QueueStateCodec.encodeLayout(state().layout())
        for (damaged in listOf<String?>(null, "", "{bad-json",
            "{\"password\":\"example\"}")) {
            val recovered = QueueStateCodec.assemble(q, damaged)!!
            assertEquals(id(1), recovered.selectedTrackId)
            assertEquals(0L, recovered.positionMs)
            assertEquals(0, recovered.repeatMode)
            assertTrue(recovered.shuffleEnabled)
            assertArrayEquals(intArrayOf(2, 0, 3, 1), recovered.shuffledIndices)
        }
    }

    @Test fun unknownPositionVersionIsNotRestored() {
        val q = QueueStateCodec.encodeLayout(state().layout())
        val p = QueueStateCodec.encodePosition(state().position())
            .replace("\"version\":1", "\"version\":5")
        assertNull(QueueStateCodec.assemble(q, p))
    }

    @Test fun neitherJsonFileContainsPathsTagsOrCredentials() {
        val s = state()
        val layout = QueueStateCodec.encodeLayout(s.layout())
        val position = QueueStateCodec.encodePosition(s.position())
        for (sensitive in listOf("encodedPath", "fileName", "title",
            "remoteEtag", "ETag", "email", "password", "Authorization", "remoteSize")) {
            assertFalse(sensitive, layout.contains(sensitive, ignoreCase = true))
            assertFalse(sensitive, position.contains(sensitive, ignoreCase = true))
        }
        assertTrue(QueueStateCodec.decodeLayout(layout) != null)
        assertTrue(QueueStateCodec.decodePosition(position) != null)
    }

    @Test fun missingCurrentUsesNextSurvivingTrackAtZero() {
        val filtered = QueueStateRestorer.filter(state(), setOf(id(1), id(2), id(4)))!!
        assertEquals(listOf(id(1), id(2), id(4)), filtered.trackIds)
        assertEquals(id(4), filtered.selectedTrackId)
        assertEquals(0L, filtered.positionMs)
    }

    @Test fun removedTrackBeforeCurrentKeepsIdAndPosition() {
        val filtered = QueueStateRestorer.filter(state(), setOf(id(1), id(3), id(4)))!!
        assertEquals(id(3), filtered.selectedTrackId)
        assertEquals(1, filtered.trackIds.indexOf(filtered.selectedTrackId))
        assertEquals(12_345L, filtered.positionMs)
    }

    @Test fun allMissingReturnsNull() {
        assertNull(QueueStateRestorer.filter(state(), emptySet()))
    }

    @Test fun filteredShuffleRemainsCompleteUniquePermutation() {
        val filtered = QueueStateRestorer.filter(state(), setOf(id(1), id(3), id(4)))!!
        assertArrayEquals(intArrayOf(1, 0, 2), filtered.shuffledIndices)
        assertEquals((0 until filtered.trackIds.size).toSet(),
            filtered.shuffledIndices!!.toSet())
    }

    @Test fun positionThrottleUsesInjectedMonotonicClock() {
        var now = 10_000L
        val throttle = QueueWriteThrottle(QueueMonotonicClock { now })
        assertEquals(0L, throttle.delayMs())
        throttle.markWritten()
        now += 1_000L
        assertEquals(4_000L, throttle.delayMs())
        assertEquals(0L, throttle.delayMs(urgent = true))
        now += 4_000L
        assertEquals(0L, throttle.delayMs())
    }

    @Test fun queueOf3590UsesOnlyFiveSynchronousSeedItems() {
        val tracks = (1..3590).map { index ->
            QueueTrack(id(index), "Track $index", "/AllMusic/$index.mp3", 1024L, null)
        }
        val selected = id(1700)
        // Warm up the pure JVM planner; Media3 time is reported on Huawei separately.
        repeat(3) { StagedPlaylistPlan.create(PlaybackQueue.create(tracks, selected)) }
        val startNs = System.nanoTime()
        val plan = StagedPlaylistPlan.create(PlaybackQueue.create(tracks, selected))
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
        assertEquals(5, plan.seed.size)
        assertTrue("JVM seed plan must stay bounded: $elapsedMs ms", elapsedMs < 50.0)
        val ordered = mutableListOf<QueueTrack>()
        ordered.addAll(plan.seed)
        while (!plan.isComplete) {
            val chunk = plan.nextChunk()!!
            assertTrue(chunk.items.size <= StagedPlaylistPlan.CHUNK_SIZE)
            if (chunk.prepend) ordered.addAll(0, chunk.items)
            else ordered.addAll(chunk.items)
        }
        assertEquals(tracks.map { it.id }, ordered.map { it.id })
    }
}
