package app.enotone.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QueueShuffleOrderTest {
    @Test fun selectedIndexIsFirstAndEveryOtherIndexAppearsOnce() {
        for (size in listOf(1, 2, 100, 3_590, 10_000)) {
            for (first in listOf(0, size / 2, size - 1).distinct()) {
                val order = QueueShuffleOrder.create(size, first, seed = 42L)
                assertEquals(size, order.size)
                assertEquals(first, order.first())
                assertEquals((0 until size).toSet(), order.toSet())
            }
        }
    }

    @Test fun sameSeedProducesSameOrder() {
        val first = QueueShuffleOrder.create(3_590, firstIndex = 1_823, seed = 7L)
        val second = QueueShuffleOrder.create(3_590, firstIndex = 1_823, seed = 7L)
        assertArrayEquals(first, second)
    }

    @Test fun invalidSizeOrAnchorIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            QueueShuffleOrder.create(0, firstIndex = 0, seed = 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            QueueShuffleOrder.create(3, firstIndex = 3, seed = 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            QueueShuffleOrder.create(3, firstIndex = -1, seed = 1L)
        }
    }
}
