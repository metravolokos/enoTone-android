package app.enotone.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class ErrorRecoveringPlayerTest {
    @Test fun logicalNextAndPreviousSuppressNativeTemporaryOrderWhenHandled() {
        val calls = Calls()
        val player = ErrorRecoveringPlayer(
            calls.player,
            onPlayRequested = {},
            onNextRequested = { calls.logicalNext++; true },
            onPreviousRequested = { calls.logicalPrevious++; true },
        )

        player.seekToNextMediaItem()
        player.seekToPreviousMediaItem()
        player.seekToNext()
        player.seekToPrevious()

        assertEquals(2, calls.logicalNext)
        assertEquals(2, calls.logicalPrevious)
        assertEquals(0, calls.nativeNext)
        assertEquals(0, calls.nativePrevious)
    }

    @Test fun nativeFallbackRunsWhenLogicalQueueDoesNotHandleCommand() {
        val calls = Calls()
        val player = ErrorRecoveringPlayer(
            calls.player,
            onPlayRequested = {},
            onNextRequested = { false },
            onPreviousRequested = { false },
        )

        player.seekToNextMediaItem()
        player.seekToPreviousMediaItem()
        player.seekToNext()
        player.seekToPrevious()

        assertEquals(2, calls.nativeNext)
        assertEquals(2, calls.nativePrevious)
    }

    private class Calls {
        var logicalNext = 0
        var logicalPrevious = 0
        var nativeNext = 0
        var nativePrevious = 0

        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
            InvocationHandler { _, method, _ ->
                when (method.name) {
                    "getPlayerError" -> null
                    "seekToNextMediaItem" -> { nativeNext++; null }
                    "seekToPreviousMediaItem" -> { nativePrevious++; null }
                    "seekToNext" -> { nativeNext++; null }
                    "seekToPrevious" -> { nativePrevious++; null }
                    else -> error("Unexpected Player call: ${method.name}")
                }
            },
        ) as Player
    }
}
