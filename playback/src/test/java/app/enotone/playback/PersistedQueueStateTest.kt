package app.enotone.playback

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistedQueueStateTest {
    @get:Rule val folder = TemporaryFolder()
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

    @Test fun savedDurationRoundTripsForTheProgressBarBeforeFirstPlay() {
        val restored = roundTrip(state().copy(durationMs = 228_000L))!!
        assertEquals(228_000L, restored.durationMs)
    }

    @Test fun checkpointWithoutDurationFromOlderBuildStillRestores() {
        val old = state()
        // Exactly the field set written before durationMs existed.
        val position = QueueStateCodec.encodePosition(old.position())
        assertFalse(position.contains("durationMs"))
        val restored = QueueStateCodec.assemble(
            QueueStateCodec.encodeLayout(old.layout()), position)!!
        assertEquals(0L, restored.durationMs)
        assertEquals(old.positionMs, restored.positionMs)
    }

    @Test fun durationIsDroppedWhenTheSelectedTrackDisappeared() {
        val s = state().copy(durationMs = 228_000L)
        val filtered = QueueStateRestorer.filter(s, setOf(id(1), id(2), id(4)))!!
        assertEquals(id(1), filtered.selectedTrackId)
        assertEquals(0L, filtered.durationMs)
    }

    @Test fun negativeDurationIsRejected() {
        val raw = QueueStateCodec.encodePosition(state().position())
            .replace("}", ",\"durationMs\":-5}")
        assertNull(QueueStateCodec.decodePosition(raw))
    }

    @Test fun noShuffleRoundTripHasNoPermutation() {
        val restored = roundTrip(state(shuffled = null))!!
        assertNull(restored.shuffledIndices)
        assertFalse(restored.shuffleEnabled)
    }

    @Test fun codecAcceptsTenThousandTracksAndRejectsTenThousandAndOne() {
        val allowed = state(
            ids = (1..10_000).map(::id),
            selected = id(5_000),
            shuffled = null,
        )
        assertTrue(QueueStateCodec.isValidLayout(allowed.layout()))
        assertEquals(10_000, requireNotNull(roundTrip(allowed)).trackIds.size)

        val rejected = allowed.copy(trackIds = allowed.trackIds + id(10_001))
        assertFalse(QueueStateCodec.isValidLayout(rejected.layout()))
        assertThrows(IllegalArgumentException::class.java) {
            QueueStateCodec.encodeLayout(rejected.layout())
        }
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
        assertEquals(id(1), filtered.selectedTrackId)
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

    @Test fun queueAtActiveLimitUsesOnlyFiveSynchronousSeedItems() {
        val tracks = (1..10_000).map { index ->
            QueueTrack(id(index), "Track $index", "/AllMusic/$index.mp3", 1024L, null)
        }
        val selected = id(51)
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

    private fun legacyLargeQueue(shuffle: Boolean, selectedIndex: Int = 1_823): PersistedQueueState {
        val ids = (1..3_647).map(::id)
        val permutation = if (shuffle) IntArray(ids.size) { ids.lastIndex - it } else null
        return PersistedQueueState(
            trackIds = ids,
            selectedTrackId = ids[selectedIndex],
            positionMs = 42_321L,
            repeatMode = 2,
            shuffleEnabled = shuffle,
            shuffledIndices = permutation,
            sourceKey = id(99_999),
            savedAtEpochMs = 1_700_000_000_000L,
            queueRevision = revision,
            durationMs = 240_000L,
        )
    }

    private fun rewriteLegacyQueue(state: PersistedQueueState): PersistedQueueState {
        val store = QueueStateFileStore.inDirectory(folder.newFolder())
        // Simulate a v1 pair written before the active cap was restored to 10 000.
        store.writeLayoutAndPosition(state.layout(), state.position())
        val diskState = store.read()!!
        assertEquals(3_647, diskState.trackIds.size)
        val reduced = QueueStateRestorer.filter(diskState, diskState.trackIds.toSet())!!
        assertEquals(3_647, reduced.trackIds.size)
        assertEquals(state.selectedTrackId, reduced.selectedTrackId)
        assertEquals(state.positionMs, reduced.positionMs)
        assertEquals(state.repeatMode, reduced.repeatMode)
        assertEquals(state.shuffleEnabled, reduced.shuffleEnabled)
        val rewrittenRevision = "11111111-2222-3333-4444-555555555555"
        store.writeLayoutAndPosition(
            reduced.layout().copy(queueRevision = rewrittenRevision),
            reduced.position().copy(queueRevision = rewrittenRevision),
        )
        val rewritten = store.read()!!
        assertEquals(3_647, rewritten.trackIds.size)
        assertEquals(state.selectedTrackId, rewritten.selectedTrackId)
        assertEquals(state.positionMs, rewritten.positionMs)
        assertEquals(state.repeatMode, rewritten.repeatMode)
        assertEquals(state.shuffleEnabled, rewritten.shuffleEnabled)
        return rewritten
    }

    @Test fun legacy3647QueueRestoresWholeOrderAndImmediatelyRewritesNormalFile() {
        val state = legacyLargeQueue(shuffle = false)
        val restored = rewriteLegacyQueue(state)
        assertEquals(state.trackIds, restored.trackIds)
        assertEquals(1_823, restored.trackIds.indexOf(restored.selectedTrackId))
        // Selection at either boundary no longer creates a 100-song window.
        val beginning = QueueStateRestorer.filter(state.copy(selectedTrackId = id(1)), state.trackIds.toSet())!!
        val ending = QueueStateRestorer.filter(state.copy(selectedTrackId = id(3_647)), state.trackIds.toSet())!!
        assertEquals(state.trackIds, beginning.trackIds)
        assertEquals(state.trackIds, ending.trackIds)
        assertEquals(0, beginning.trackIds.indexOf(beginning.selectedTrackId))
        assertEquals(3_646, ending.trackIds.indexOf(ending.selectedTrackId))
    }

    @Test fun legacy3647ShuffleRestoresSavedOrderAndImmediatelyRewritesFile() {
        val state = legacyLargeQueue(shuffle = true)
        val rewritten = rewriteLegacyQueue(state)
        val playbackOrder = rewritten.shuffledIndices!!.map { rewritten.trackIds[it] }
        val oldPlaybackOrder = state.shuffledIndices!!.map { state.trackIds[it] }
        assertEquals(oldPlaybackOrder, playbackOrder)
        assertEquals(state.selectedTrackId, playbackOrder[1_823])
        assertEquals(3_647, rewritten.trackIds.size)
        assertEquals(3_647, rewritten.shuffledIndices.toSet().size)
    }

    /**
     * Файлы, записанные прежней реализацией на Gson (до 2026-11-13), читаются без потерь:
     * на устройстве владельца лежит чекпоинт старого формата.
     */
    @Test fun checkpointsWrittenByGsonVersionStayReadable() {
        val v = QueueStateCodec.VERSION
        val layout = """{"version":$v,"trackIds":["${id(1)}","${id(2)}","${id(3)}","${id(4)}"],""" +
            """"shuffleEnabled":true,"shuffledIndices":[2,0,3,1],"sourceKey":"${id(99)}",""" +
            """"queueRevision":"$revision","savedAtEpochMs":12345678}"""
        val position = """{"version":$v,"selectedTrackId":"${id(3)}","positionMs":12345,""" +
            """"repeatMode":2,"shuffleEnabled":true,"savedAtEpochMs":12345678,""" +
            """"queueRevision":"$revision","durationMs":240000}"""
        val restored = QueueStateCodec.assemble(layout, position)!!
        assertEquals(listOf(id(1), id(2), id(3), id(4)), restored.trackIds)
        assertArrayEquals(intArrayOf(2, 0, 3, 1), restored.shuffledIndices)
        assertEquals(id(3), restored.selectedTrackId)
        assertEquals(12_345L, restored.positionMs)
        assertEquals(240_000L, restored.durationMs)
        assertEquals(QueueRestoreCode.RESTORED, QueueStateCodec.inspect(layout, position).code)
        // Без необязательного durationMs (чекпоинты до #96) — тоже читается.
        val legacy = position.replace(""","durationMs":240000""", "")
        assertEquals(0L, QueueStateCodec.assemble(layout, legacy)!!.durationMs)
    }

    @Test fun unknownFieldsAndMalformedJsonAreRejectedWithoutExceptions() {
        val good = QueueStateCodec.encodePosition(state().position())
        assertNull(QueueStateCodec.decodePosition(good.replace("}", ""","extra":1}""")))
        assertNull(QueueStateCodec.decodePosition("{not json"))
        assertNull(QueueStateCodec.decodeLayout("""{"version":"x"}"""))
    }
}
