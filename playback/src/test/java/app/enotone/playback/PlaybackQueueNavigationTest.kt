package app.enotone.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueNavigationTest {
    private val ids = (1..5).map { it.toString() }

    private fun navigation() = PlaybackQueueNavigation().apply {
        updateOrder(ids, ids.withIndex().associate { it.value to it.index }, null, false)
    }

    @Test fun ordinaryTrackTransitionCancelsAnUnloadedDeferredSelection() {
        val nav = navigation()
        assertTrue(nav.selectPosition(
            position = 4,
            currentTrackId = ids.first(),
            seekLoadedTarget = { false },
            stageMoreItems = { true },
        ))
        assertTrue(nav.hasPendingSeek)

        nav.onMediaItemTransition()

        assertFalse(nav.hasPendingSeek)
        assertFalse(nav.applyPendingSeek { error("stale selection must not seek") })
    }

    @Test fun anyMediaItemTransitionCancelsDeferredSelection() {
        val nav = navigation()
        assertTrue(nav.selectPosition(
            position = 4,
            currentTrackId = ids.first(),
            seekLoadedTarget = { false },
            stageMoreItems = { true },
        ))

        nav.onMediaItemTransition()

        assertFalse(nav.hasPendingSeek)
    }

    @Test fun manualNextPlaysNextSongInsteadOfTheUnloadedSelection() {
        val nav = navigation()
        assertTrue(nav.selectPosition(
            position = 4,
            currentTrackId = ids.first(),
            seekLoadedTarget = { false },
            stageMoreItems = { true },
        ))

        val current = nav.snapshot(ids.first())!!
        val nextTrackId = current.trackIds[current.currentIndex + 1]
        nav.onMediaItemTransition()

        assertEquals(ids[1], nextTrackId)
        assertFalse(nav.hasPendingSeek)
        assertFalse(nav.applyPendingSeek { error("manual action canceled the selection") })
    }

    @Test fun manualPreviousPlaysPreviousSongInsteadOfTheUnloadedSelection() {
        val nav = navigation()
        assertTrue(nav.selectPosition(
            position = 4,
            currentTrackId = ids[2],
            seekLoadedTarget = { false },
            stageMoreItems = { true },
        ))

        val current = nav.snapshot(ids[2])!!
        val previousTrackId = current.trackIds[current.currentIndex - 1]
        nav.onMediaItemTransition()

        assertEquals(ids[1], previousTrackId)
        assertFalse(nav.hasPendingSeek)
        assertFalse(nav.applyPendingSeek { error("manual action canceled the selection") })
    }

    @Test fun manualSeekClearsTheDeferredSelection() {
        val nav = navigation()
        assertTrue(nav.selectPosition(
            position = 4,
            currentTrackId = ids.first(),
            seekLoadedTarget = { false },
            stageMoreItems = { true },
        ))

        nav.onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK)

        assertFalse(nav.hasPendingSeek)
        assertFalse(nav.applyPendingSeek { error("manual action canceled the selection") })
    }

    @Test fun nonUserPositionDiscontinuityKeepsDeferredSelection() {
        val nav = navigation()
        assertTrue(nav.selectPosition(
            position = 4,
            currentTrackId = ids.first(),
            seekLoadedTarget = { false },
            stageMoreItems = { true },
        ))

        nav.onPositionDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION)

        assertTrue(nav.hasPendingSeek)
    }
}
