package app.enotone.playback

import androidx.media3.common.Player
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackContinuationPolicyTest {
    @Test fun recoversOnlyUnexpectedEndWithRequestedPlaybackAndNextItem() {
        assertTrue(PlaybackContinuationPolicy.shouldAutoRecover(
            ended = true, playWhenReady = true, hasNext = true,
            repeatOne = false, unsuppressed = true))
        assertFalse(PlaybackContinuationPolicy.shouldAutoRecover(
            ended = false, playWhenReady = true, hasNext = true,
            repeatOne = false, unsuppressed = true))
        assertFalse(PlaybackContinuationPolicy.shouldAutoRecover(
            ended = true, playWhenReady = true, hasNext = false,
            repeatOne = false, unsuppressed = true))
    }

    @Test fun neverOverridesExplicitPauseOrFocusLoss() {
        assertFalse(PlaybackContinuationPolicy.shouldAutoRecover(
            ended = true, playWhenReady = false, hasNext = true,
            repeatOne = false, unsuppressed = true))
        assertFalse(PlaybackContinuationPolicy.shouldAutoRecover(
            ended = true, playWhenReady = true, hasNext = true,
            repeatOne = false, unsuppressed = false))
    }

    private class FakeEndedPlayer(
        private val repeat: Int,
        private val count: Int,
        var index: Int,
        private val wantsPlay: Boolean = true,
    ) {
        var state = Player.STATE_ENDED
        var replaySeeks = 0
        var nextSeeks = 0
        var playCalls = 0

        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
            InvocationHandler { _, method, _ ->
                when (method.name) {
                    "getPlaybackState" -> state
                    "getPlayWhenReady" -> wantsPlay
                    "getRepeatMode" -> repeat
                    "hasNextMediaItem" -> index < count - 1
                    "getPlaybackSuppressionReason" ->
                        Player.PLAYBACK_SUPPRESSION_REASON_NONE
                    "seekToDefaultPosition" -> {
                        replaySeeks++
                        state = Player.STATE_READY
                        null
                    }
                    "seekToNextMediaItem" -> {
                        nextSeeks++
                        index++
                        state = Player.STATE_READY
                        null
                    }
                    "prepare" -> {
                        state = Player.STATE_READY
                        null
                    }
                    "play" -> {
                        playCalls++
                        null
                    }
                    else -> error("Unexpected Player call: ${method.name}")
                }
            },
        ) as Player
    }

    @Test fun repeatOneUnexpectedEndRestartsCurrentWithoutChangingQueuePosition() {
        for (count in listOf(1, 3)) {
            val selectedIndex = if (count == 1) 0 else 1
            val fake = FakeEndedPlayer(Player.REPEAT_MODE_ONE, count, selectedIndex)
            assertTrue(PlaybackContinuationPolicy.recoverUnexpectedEnd(fake.player))
            assertEquals(selectedIndex, fake.index)
            assertEquals(1, fake.replaySeeks)
            assertEquals(0, fake.nextSeeks)
            assertEquals(1, fake.playCalls)
        }
    }

    @Test fun repeatOffAdvancesButExplicitPauseNeverRestarts() {
        val off = FakeEndedPlayer(Player.REPEAT_MODE_OFF, 3, 1)
        assertTrue(PlaybackContinuationPolicy.recoverUnexpectedEnd(off.player))
        assertEquals(2, off.index)
        assertEquals(1, off.nextSeeks)
        assertEquals(0, off.replaySeeks)

        val paused = FakeEndedPlayer(Player.REPEAT_MODE_ONE, 3, 1, wantsPlay = false)
        assertFalse(PlaybackContinuationPolicy.recoverUnexpectedEnd(paused.player))
        assertEquals(1, paused.index)
        assertEquals(0, paused.replaySeeks)
        assertEquals(0, paused.playCalls)
    }

    @Test fun repeatOneMustRecoverUnexpectedEndEvenForSingleTrackQueue() {
        // Если Media3 неожиданно дошёл до ENDED в режиме ONE, текущий
        // трек должен запускаться заново независимо от наличия Next.
        for (hasNext in listOf(false, true)) {
            assertTrue(PlaybackContinuationPolicy.shouldAutoRecover(
                ended = true, playWhenReady = true, hasNext = hasNext,
                repeatOne = true, unsuppressed = true))
        }
    }
}
