package app.enotone.playback

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real filesystem and pure JVM: no Robolectric, WebDAV or Media3 Service. */
class QueueStateFileStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val first = "a".repeat(64)
    private val second = "b".repeat(64)
    private val source = "c".repeat(64)
    private val rev1 = "01234567-89ab-cdef-0123-456789abcdef"
    private val rev2 = "12345678-89ab-cdef-0123-456789abcdef"

    private fun layout(revision: String = rev1) = PersistedQueueLayout(
        trackIds = listOf(first, second), shuffledIndices = null,
        shuffleEnabled = false, sourceKey = source,
        queueRevision = revision, savedAtEpochMs = 123L,
    )
    private fun position(revision: String = rev1) = PersistedPositionState(
        selectedTrackId = second, positionMs = 42_000L, repeatMode = 2,
        shuffleEnabled = false, savedAtEpochMs = 456L, queueRevision = revision,
    )

    @Test fun completePairRestoresMatchingRevision() {
        val store = QueueStateFileStore.inDirectory(folder.root)
        assertEquals(QueueRestoreCode.NO_LAYOUT_FILE, store.readWithStatus().code)
        store.writeLayoutAndPosition(layout(), position())
        val result = store.readWithStatus()
        assertEquals(QueueRestoreCode.RESTORED, result.code)
        assertEquals(rev1, result.state?.queueRevision)
        assertEquals(second, result.state?.selectedTrackId)
        assertEquals(42_000L, result.state?.positionMs)
        assertEquals(QueueRestoreCode.RESTORED,
            QueuePersistenceDiagnostics.state.value.readResult)
    }

    @Test fun failedPositionWriteRollsBackOldPairAndCountsError() {
        var failPosition = false
        val store = QueueStateFileStore.inDirectory(folder.root) {
            if (failPosition) throw IOException("test-only injected position fault")
        }
        store.writeLayoutAndPosition(layout(), position())
        val before = QueuePersistenceDiagnostics.state.value.writeErrors
        failPosition = true
        var threw = false
        try {
            store.writeLayoutAndPosition(layout(rev2), position(rev2))
        } catch (_: IOException) {
            threw = true
        }
        assertTrue(threw)
        failPosition = false
        val recovered = store.readWithStatus()
        assertEquals(QueueRestoreCode.RESTORED, recovered.code)
        assertEquals(rev1, recovered.state?.queueRevision)
        assertEquals(second, recovered.state?.selectedTrackId)
        assertEquals(before + 1, QueuePersistenceDiagnostics.state.value.writeErrors)
        assertEquals("IOException", QueuePersistenceDiagnostics.state.value.lastExceptionClass)
    }

    @Test fun inspectDistinguishesInvalidFilesVersionsRevisionsAndShuffle() {
        val encodedLayout = QueueStateCodec.encodeLayout(layout())
        val encodedPosition = QueueStateCodec.encodePosition(position())
        fun code(l: String?, p: String?) = QueueStateCodec.inspect(l, p).code
        assertEquals(QueueRestoreCode.NO_LAYOUT_FILE, code(null, null))
        assertEquals(QueueRestoreCode.LAYOUT_INVALID, code("{", encodedPosition))
        assertEquals(QueueRestoreCode.POSITION_INVALID, code(encodedLayout, "{"))
        assertNotNull(QueueStateCodec.inspect(encodedLayout, "{").state)
        assertEquals(QueueRestoreCode.REVISION_MISMATCH,
            code(encodedLayout, QueueStateCodec.encodePosition(position(rev2))))
        assertEquals(QueueRestoreCode.SHUFFLE_MISMATCH,
            code(encodedLayout, QueueStateCodec.encodePosition(position().copy(shuffleEnabled = true))))
        assertEquals(QueueRestoreCode.FOREIGN_VERSION,
            code(encodedLayout.replace("\"version\":1", "\"version\":9"), encodedPosition))
        assertEquals(QueueRestoreCode.FOREIGN_VERSION,
            code(encodedLayout, encodedPosition.replace("\"version\":1", "\"version\":9")))
        assertEquals(QueueRestoreCode.RESTORED, code(encodedLayout, encodedPosition))
        assertNull(QueueStateCodec.inspect(encodedLayout,
            QueueStateCodec.encodePosition(position(rev2))).state)
    }

    @Test fun invalidShuffleCannotReplaceValidPreviousPair() {
        val store = QueueStateFileStore.inDirectory(folder.root)
        store.writeLayoutAndPosition(layout(), position())
        var threw = false
        try {
            store.writeLayoutAndPosition(
                layout(rev2).copy(shuffleEnabled = true, shuffledIndices = null),
                position(rev2).copy(shuffleEnabled = true),
            )
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
        val result = store.readWithStatus()
        assertEquals(QueueRestoreCode.RESTORED, result.code)
        assertEquals(rev1, result.state?.queueRevision)
    }

    /** Error, а не Exception: откат в writeLayoutAndPosition не выполняется — как при гибели процесса. */
    private class SimulatedProcessDeath : Error()

    @Test fun processDeathBetweenLayoutAndPositionRestoresPreviousPair() {
        var die = false
        val store = QueueStateFileStore.inDirectory(folder.root) {
            if (die) throw SimulatedProcessDeath()
        }
        store.writeLayoutAndPosition(layout(), position())
        die = true
        try {
            store.writeLayoutAndPosition(layout(rev2).copy(trackIds = listOf(second, first)), position(rev2))
        } catch (_: SimulatedProcessDeath) {
            // Процесс «умер»: новая структура записана, позиция — старая.
        }
        val restarted = QueueStateFileStore.inDirectory(folder.root)
        val result = restarted.readWithStatus()
        assertEquals(QueueRestoreCode.RESTORED, result.code)
        assertEquals(rev1, result.state?.queueRevision)
        assertEquals(listOf(first, second), result.state?.trackIds)
        assertEquals(42_000L, result.state?.positionMs)
    }

    @Test fun completedPairWinsOverPreviousLayout() {
        val store = QueueStateFileStore.inDirectory(folder.root)
        store.writeLayoutAndPosition(layout(), position())
        store.writeLayoutAndPosition(layout(rev2), position(rev2))
        assertEquals(rev2, store.readWithStatus().state?.queueRevision)
        store.clear()
        assertEquals(QueueRestoreCode.NO_LAYOUT_FILE, store.readWithStatus().code)
    }
}
