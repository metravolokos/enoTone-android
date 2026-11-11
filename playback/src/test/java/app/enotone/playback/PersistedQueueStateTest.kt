package app.enotone.playback

import org.junit.Assert.*
import org.junit.Test

class PersistedQueueStateTest {
    private fun id(number: Int): String = number.toString(16).padStart(64, '0')
    private fun snapshot(
        ids: List<String> = listOf(id(1), id(2), id(3), id(4)),
        selected: String = id(3),
        shuffled: IntArray? = intArrayOf(2, 0, 3, 1),
    ) = PersistedQueueState(
        trackIds = ids, selectedTrackId = selected, positionMs = 12_345L,
        repeatMode = 2, shuffleEnabled = shuffled != null, shuffledIndices = shuffled,
        sourceKey = id(99), savedAtEpochMs = 12345678L,
    )

    @Test fun roundTripPreservesOriginalAndShuffledOrder() {
        val initial = snapshot()
        val restored = QueueStateCodec.decode(QueueStateCodec.encode(initial))!!
        assertEquals(initial.trackIds, restored.trackIds)
        assertEquals(initial.selectedTrackId, restored.selectedTrackId)
        assertEquals(initial.positionMs, restored.positionMs)
        assertEquals(initial.repeatMode, restored.repeatMode)
        assertArrayEquals(initial.shuffledIndices, restored.shuffledIndices)
        assertEquals(initial.sourceKey, restored.sourceKey)
    }

    @Test fun damagedEmptyAndUnknownVersionReturnNull() {
        val good = QueueStateCodec.encode(snapshot())
        assertNull(QueueStateCodec.decode(""))
        assertNull(QueueStateCodec.decode("{not-json"))
        assertNull(QueueStateCodec.decode(good.replace("\"version\":1", "\"version\":9")))
        assertNull(QueueStateCodec.decode(good.dropLast(9)))
        assertNull(QueueStateCodec.decode(good.replace("\"selectedTrackId\"", "\"password\"")))
    }

    @Test fun jsonContainsIdentifiersOnlyNotMetadataOrSecrets() {
        val json = QueueStateCodec.encode(snapshot())
        for (name in listOf("encodedPath", "title", "fileName", "ETag",
            "email", "password", "Authorization", "remoteSize", "remoteEtag")) {
            assertFalse(name, json.contains(name, ignoreCase = true))
        }
        assertEquals(null, QueueStateCodec.decode(json.replace(
            "\"sourceKey\"", "\"email\"")))
    }

    @Test fun removedSelectedUsesNextAndResetsPosition() {
        val filtered = QueueStateRestorer.filter(snapshot(), setOf(id(1), id(2), id(4)))!!
        assertEquals(listOf(id(1), id(2), id(4)), filtered.trackIds)
        assertEquals(id(4), filtered.selectedTrackId)
        assertEquals(0L, filtered.positionMs)
    }

    @Test fun removedBeforeSelectedMovesIndexWithoutChangingPosition() {
        val filtered = QueueStateRestorer.filter(snapshot(), setOf(id(1), id(3), id(4)))!!
        assertEquals(id(3), filtered.selectedTrackId)
        assertEquals(1, filtered.trackIds.indexOf(filtered.selectedTrackId))
        assertEquals(12_345L, filtered.positionMs)
    }

    @Test fun allRemovedReturnsNull() {
        assertNull(QueueStateRestorer.filter(snapshot(), emptySet()))
    }

    @Test fun shuffleAfterFilteringIsStillAnExactPermutation() {
        val filtered = QueueStateRestorer.filter(snapshot(), setOf(id(1), id(3), id(4)))!!
        assertArrayEquals(intArrayOf(1, 0, 2), filtered.shuffledIndices)
        assertEquals((0 until filtered.trackIds.size).toSet(),
            filtered.shuffledIndices!!.toSet())
    }

    @Test fun throttlingUsesInjectedClockAndUrgentEventsBypass() {
        var time = 10_000L
        val throttle = QueueWriteThrottle(QueueMonotonicClock { time })
        assertEquals(0L, throttle.delayMs())
        throttle.markWritten()
        time += 1_000L
        assertEquals(4_000L, throttle.delayMs())
        assertEquals(0L, throttle.delayMs(urgent = true))
        time += 4_000L
        assertEquals(0L, throttle.delayMs())
    }
}
