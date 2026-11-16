package app.enotone.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.enotone.playback.NextBufferState
import app.enotone.playback.PlaybackModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Uses transport callbacks, not Media3/network, to verify the rendered controls. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class NowPlayingUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun transportButtonsCallTheirActionsAndVolumeIsAbsent() {
        var close = 0
        var play = 0
        var previous = 0
        var next = 0
        var shuffle = 0
        var repeat = 0
        rule.setContent {
            EnoToneTheme {
                NowPlayingScreen(
                    trackId = "fixture-id", title = "Fixture title", artist = "Fixture artist",
                    album = "Fixture album", artwork = null, playbackState = "PAUSED",
                    errorCode = 0, positionMs = 15000, durationMs = 120000,
                    canSeek = true, controlsEnabled = true,
                    hasPrevious = true, hasNext = true,
                    queuePosition = 2, queueTotal = 3,
                    repeatMode = Player.REPEAT_MODE_OFF, shuffleEnabled = false,
                    onClose = { close++ }, onSeekFraction = {}, onPlayPause = { play++ },
                    onPrevious = { previous++ }, onNext = { next++ },
                    onCycleRepeat = { repeat++ }, onToggleShuffle = { shuffle++ },
                )
            }
        }
        rule.onNodeWithText("Fixture title").assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.playback_resume)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.playback_previous)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.playback_next)).performClick()
        rule.onNode(hasContentDescription(context.getString(R.string.playback_shuffle_off))
            and hasClickAction()).performClick()
        rule.runOnIdle {
            assertEquals(0, close)
            assertEquals(1, play)
            assertEquals(1, previous)
            assertEquals(1, next)
            assertEquals(1, shuffle)
        }
        rule.onNodeWithContentDescription(
            context.getString(R.string.playback_repeat_control,
                context.getString(R.string.playback_repeat_off)),
        ).performClick()
        rule.runOnIdle { assertEquals(1, repeat) }
        // D3.2 (владелец): кнопки/ручки «Свернуть» нет — экран закрывается только жестом вниз.
        rule.onNodeWithContentDescription(context.getString(R.string.library_player_close))
            .assertDoesNotExist()
        // Короткий медленный свайп не закрывает плеер (D3.4), он возвращается на место.
        rule.onRoot().performTouchInput {
            swipe(Offset(center.x, top + 70f), Offset(center.x, top + 110f), durationMillis = 600)
        }
        rule.runOnIdle { assertEquals(0, close) }
        rule.onRoot().performTouchInput { swipeDown(startY = top + 40f, endY = bottom - 40f) }
        rule.runOnIdle { assertEquals(1, close) }
        rule.onNodeWithContentDescription("Громкость").assertDoesNotExist()
        rule.onNodeWithContentDescription("Volume").assertDoesNotExist()
        // No invented quality labels or non-functional controls.
        for (label in listOf("Lyrics", "Cast", "Favorites", "Queue", "Lossless",
            "Текст песни", "Избранное", "Очередь", "Без потерь")) {
            rule.onNodeWithContentDescription(label).assertDoesNotExist()
            rule.onNodeWithText(label).assertDoesNotExist()
        }
    }

    @Test fun repeatCycleAndShuffleRespondToRealTouchesAndRecompose() {
        val repeat = mutableIntStateOf(Player.REPEAT_MODE_OFF)
        val shuffle = mutableStateOf(false)
        rule.setContent {
            EnoToneTheme {
                NowPlayingScreen(
                    trackId = "fixture-id", title = "Fixture", artist = null,
                    album = null, artwork = null, playbackState = "PAUSED",
                    errorCode = 0, positionMs = 0, durationMs = 60_000,
                    canSeek = true, controlsEnabled = true,
                    hasPrevious = true, hasNext = true,
                    queuePosition = 1, queueTotal = 3,
                    repeatMode = repeat.intValue, shuffleEnabled = shuffle.value,
                    onClose = {}, onSeekFraction = {}, onPlayPause = {},
                    onPrevious = {}, onNext = {},
                    onCycleRepeat = {
                        repeat.intValue = PlaybackModes.nextRepeat(repeat.intValue)
                    },
                    onToggleShuffle = { shuffle.value = !shuffle.value },
                )
            }
        }

        fun repeatLabel(mode: Int): String {
            val labelId = when (mode) {
                Player.REPEAT_MODE_ONE -> R.string.playback_repeat_one
                Player.REPEAT_MODE_ALL -> R.string.playback_repeat_all
                else -> R.string.playback_repeat_off
            }
            return context.getString(R.string.playback_repeat_control,
                context.getString(labelId))
        }

        for ((from, to) in listOf(
            Player.REPEAT_MODE_OFF to Player.REPEAT_MODE_ONE,
            Player.REPEAT_MODE_ONE to Player.REPEAT_MODE_ALL,
            Player.REPEAT_MODE_ALL to Player.REPEAT_MODE_OFF,
        )) {
            rule.onNodeWithContentDescription(repeatLabel(from))
                .performTouchInput { click() }
            rule.onNodeWithContentDescription(repeatLabel(to)).assertExists()
            rule.runOnIdle { assertEquals(to, repeat.intValue) }
        }
        rule.onNodeWithContentDescription(context.getString(R.string.playback_shuffle_off))
            .performTouchInput { click() }
        rule.onNodeWithContentDescription(context.getString(R.string.playback_shuffle_on))
            .assertExists()
        rule.runOnIdle { assertEquals(true, shuffle.value) }
        rule.onNodeWithContentDescription(context.getString(R.string.playback_shuffle_on))
            .performTouchInput { click() }
        rule.onNodeWithContentDescription(context.getString(R.string.playback_shuffle_off))
            .assertExists()
        rule.runOnIdle { assertEquals(false, shuffle.value) }
    }

    @Test fun disabledTransportIsNotClickableAndKeepsTimeVisible() {
        var play = 0
        rule.setContent {
            EnoToneTheme {
                NowPlayingScreen(
                    trackId = "fixture-id", title = "Paused title", artist = null,
                    album = null, artwork = null, playbackState = "PAUSED", errorCode = 0,
                    positionMs = 30000, durationMs = 120000,
                    canSeek = false, controlsEnabled = false,
                    hasPrevious = false, hasNext = false,
                    queuePosition = 1, queueTotal = 1,
                    repeatMode = Player.REPEAT_MODE_OFF, shuffleEnabled = false,
                    onClose = {}, onSeekFraction = {}, onPlayPause = { play++ },
                    onPrevious = {}, onNext = {}, onCycleRepeat = {}, onToggleShuffle = {},
                )
            }
        }
        rule.onNodeWithText("00:30").assertExists()
        rule.onNodeWithText("-01:30").assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.playback_resume)).assertExists()
        rule.runOnIdle { assertEquals(0, play) }
    }

    @Test fun bufferingDescriptionsFollowKnownDurationAndNextTrackState() = withAnimatorDurationScale(0f) {
        val next = mutableStateOf(NextBufferState.NONE)
        rule.setContent {
            EnoToneTheme {
                NowPlayingScreen(
                    trackId = "fixture-id", title = "Fixture", artist = null,
                    album = null, artwork = null, playbackState = "PLAYING", errorCode = 0,
                    positionMs = 86_000L, durationMs = 228_000L,
                    canSeek = true, controlsEnabled = true,
                    hasPrevious = true, hasNext = true,
                    queuePosition = 1, queueTotal = 3,
                    repeatMode = Player.REPEAT_MODE_OFF, shuffleEnabled = false,
                    bufferedFraction = 0.64f, hasKnownBufferDuration = true,
                    nextBufferState = next.value,
                    onClose = {}, onSeekFraction = {}, onPlayPause = {},
                    onPrevious = {}, onNext = {}, onCycleRepeat = {}, onToggleShuffle = {},
                )
            }
        }
        val seek = context.getString(R.string.now_playing_seek_known, "01:26", "03:48", 64)
        val loading = context.getString(R.string.now_playing_next_loading)
        val ready = context.getString(R.string.now_playing_next_ready)
        rule.onNodeWithContentDescription(seek).assertExists()
        rule.onNodeWithContentDescription(loading).assertDoesNotExist()
        rule.onNodeWithContentDescription(ready).assertDoesNotExist()

        rule.runOnIdle { next.value = NextBufferState.LOADING }
        rule.onNodeWithContentDescription(loading).assertExists()
        rule.runOnIdle { next.value = NextBufferState.READY }
        rule.onNodeWithContentDescription(ready).assertExists()
        rule.onNodeWithContentDescription(loading).assertDoesNotExist()
        rule.runOnIdle { next.value = NextBufferState.NONE }
        rule.onNodeWithContentDescription(ready).assertDoesNotExist()
    }

    @Test fun loadingDotRemainsStaticWhenSystemAnimationsAreDisabled() = withAnimatorDurationScale(0f) {
        assertFalse(NextTrackLoadingMotion.shouldPulse(0f))
        assertEquals(0.6f, NextTrackLoadingMotion.alpha(0f, 1f), 0f)
        assertTrue(NextTrackLoadingMotion.shouldPulse(1f))
        assertEquals(0.42f, NextTrackLoadingMotion.alpha(1f, 0.42f), 0f)

        var hostView: View? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val current = LocalView.current
            SideEffect { hostView = current }
            EnoToneTheme {
                NowPlayingScreen(
                    trackId = "fixture-id", title = "Fixture", artist = null,
                    album = null, artwork = null, playbackState = "PLAYING", errorCode = 0,
                    positionMs = 10_000L, durationMs = 120_000L,
                    canSeek = true, controlsEnabled = true,
                    hasPrevious = true, hasNext = true,
                    queuePosition = 1, queueTotal = 3,
                    repeatMode = Player.REPEAT_MODE_OFF, shuffleEnabled = false,
                    hasKnownBufferDuration = true,
                    nextBufferState = NextBufferState.LOADING,
                    onClose = {}, onSeekFraction = {}, onPlayPause = {},
                    onPrevious = {}, onNext = {}, onCycleRepeat = {}, onToggleShuffle = {},
                )
            }
        }
        val loading = rule.onNodeWithContentDescription(
            context.getString(R.string.now_playing_next_loading),
        )
        loading.assertExists()
        val initial = loadingDotPixel(requireNotNull(hostView))
        rule.mainClock.advanceTimeBy(550)
        val later = loadingDotPixel(requireNotNull(hostView))
        assertEquals("LOADING remains visually static at animator scale 0", initial, later)
    }

    @Test fun loadingDotPulsesAtNormalSystemAnimationScale() = withAnimatorDurationScale(1f) {
        var hostView: View? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val current = LocalView.current
            SideEffect { hostView = current }
            EnoToneTheme {
                NowPlayingScreen(
                    trackId = "fixture-id", title = "Fixture", artist = null,
                    album = null, artwork = null, playbackState = "PLAYING", errorCode = 0,
                    positionMs = 10_000L, durationMs = 120_000L,
                    canSeek = true, controlsEnabled = true,
                    hasPrevious = true, hasNext = true,
                    queuePosition = 1, queueTotal = 3,
                    repeatMode = Player.REPEAT_MODE_OFF, shuffleEnabled = false,
                    hasKnownBufferDuration = true,
                    nextBufferState = NextBufferState.LOADING,
                    onClose = {}, onSeekFraction = {}, onPlayPause = {},
                    onPrevious = {}, onNext = {}, onCycleRepeat = {}, onToggleShuffle = {},
                )
            }
        }
        val loading = rule.onNodeWithContentDescription(
            context.getString(R.string.now_playing_next_loading),
        )
        loading.assertExists()
        val atStart = loadingDotPixel(requireNotNull(hostView))
        rule.mainClock.advanceTimeBy(550)
        val afterHalfPeriod = loadingDotPixel(requireNotNull(hostView))
        assertTrue("LOADING dot should change pixels at 550 ms", atStart != afterHalfPeriod)
    }

    @Test fun unknownDurationOmitsBufferedPercentage() {
        rule.setContent {
            EnoToneTheme {
                NowPlayingScreen(
                    trackId = "fixture-id", title = "Fixture", artist = null,
                    album = null, artwork = null, playbackState = "PLAYING", errorCode = 0,
                    positionMs = 86_000L, durationMs = 0L,
                    canSeek = false, controlsEnabled = true,
                    hasPrevious = true, hasNext = true,
                    queuePosition = 1, queueTotal = 3,
                    repeatMode = Player.REPEAT_MODE_OFF, shuffleEnabled = false,
                    bufferedFraction = 0.9f, hasKnownBufferDuration = false,
                    nextBufferState = NextBufferState.READY,
                    onClose = {}, onSeekFraction = {}, onPlayPause = {},
                    onPrevious = {}, onNext = {}, onCycleRepeat = {}, onToggleShuffle = {},
                )
            }
        }
        val unknown = context.getString(R.string.now_playing_seek_unknown, "01:26")
        rule.onNodeWithContentDescription(unknown).assertExists()
        rule.onNodeWithContentDescription(
            context.getString(R.string.now_playing_next_ready),
        ).assertExists()
        val known = context.getString(R.string.now_playing_seek_known, "01:26", "03:48", 90)
        rule.onNodeWithContentDescription(known).assertDoesNotExist()
    }

    @Test fun readyDotDoesNotInterceptHorizontalSeekOrVerticalClose() {
        var close = 0
        var sought: Float? = null
        rule.setContent {
            EnoToneTheme {
                NowPlayingScreen(
                    trackId = "fixture-id", title = "Fixture", artist = null,
                    album = null, artwork = null, playbackState = "PLAYING", errorCode = 0,
                    positionMs = 86_000L, durationMs = 228_000L,
                    canSeek = true, controlsEnabled = true,
                    hasPrevious = true, hasNext = true,
                    queuePosition = 1, queueTotal = 3,
                    repeatMode = Player.REPEAT_MODE_OFF, shuffleEnabled = false,
                    bufferedFraction = 0.9f, hasKnownBufferDuration = true,
                    nextBufferState = NextBufferState.READY,
                    onClose = { close++ }, onSeekFraction = { sought = it }, onPlayPause = {},
                    onPrevious = {}, onNext = {}, onCycleRepeat = {}, onToggleShuffle = {},
                )
            }
        }
        val seek = context.getString(R.string.now_playing_seek_known, "01:26", "03:48", 90)
        val slider = rule.onNodeWithContentDescription(seek)
        rule.onNodeWithContentDescription(
            context.getString(R.string.now_playing_next_ready),
        ).assertExists()
        slider.performTouchInput {
            swipe(Offset(left + 24f, center.y), Offset(right - 24f, center.y), durationMillis = 300)
        }
        rule.runOnIdle { assertTrue(sought != null) }
        rule.onRoot().performTouchInput { swipeDown(startY = top + 36f, endY = bottom - 36f) }
        rule.runOnIdle { assertEquals(1, close) }
    }

    private fun withAnimatorDurationScale(scale: Float, block: () -> Unit) {
        val key = android.provider.Settings.Global.ANIMATOR_DURATION_SCALE
        val original = android.provider.Settings.Global.getFloat(context.contentResolver, key, 1f)
        android.provider.Settings.Global.putFloat(context.contentResolver, key, scale)
        try {
            block()
        } finally {
            android.provider.Settings.Global.putFloat(context.contentResolver, key, original)
        }
    }

    private fun loadingDotPixel(view: View): Int {
        val bounds = rule.onNodeWithContentDescription(
            context.getString(R.string.now_playing_next_loading),
        ).fetchSemanticsNode().boundsInRoot
        var pixel = 0
        rule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                pixel = bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt())
            } finally {
                bitmap.recycle()
            }
        }
        return pixel
    }
}
