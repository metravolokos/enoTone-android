package app.enotone.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import app.enotone.library.OfflineTrackRow
import app.enotone.playback.PlaybackQueueSnapshot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.enotone.android.core.ui.theme.EnoToneTheme as EnoTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w377dp-h816dp-xxxhdpi")
class QueueSheetUiTest {
    @get:Rule val rule = createComposeRule()

    private val ids = (0 until 1_000).map(::trackId)
    private val tracks = ids.associateWith { id ->
        val number = id.removePrefix("queue-").toInt()
        OfflineTrackRow(
            id = id,
            title = "Песня ${number.toString().padStart(3, '0')}",
            artist = "Исполнитель ${number % 7 + 1}",
            album = "Альбом очереди",
        )
    }

    @Test fun opensAtCurrentAndOnePreviousAndCurrentTapDoesNothing() {
        var selected = -1
        val order = ids.take(12)
        rule.setContent {
            EnoTheme(darkTheme = true) {
                QueueSheet(
                    visible = true,
                    queue = PlaybackQueueSnapshot(order, currentIndex = 5),
                    shuffleEnabled = false,
                    isPlaying = true,
                    tracksById = tracks,
                    artwork = { Box(Modifier.size(44.dp).background(Color(0xFF536274))) },
                    onDismiss = {},
                    onSelectPosition = { selected = it },
                )
            }
        }
        rule.onNodeWithTag("queue-track-4").assertIsDisplayed()
        rule.onNodeWithTag("queue-track-5").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(-1, selected) }
        rule.onNodeWithText("Далее").assertIsDisplayed()
        rule.onNodeWithTag("queue-track-4").performClick()
        rule.runOnIdle { assertEquals(4, selected) }
        // The row reuses EnoTrackRow without its overflow action.
        rule.onNodeWithContentDescription("Действия: Песня 004").assertDoesNotExist()
    }

    @Test fun currentHighlightChangesWithoutMovingTheList() {
        val current = mutableIntStateOf(50)
        val order = ids.take(100)
        rule.setContent {
            EnoTheme(darkTheme = false) {
                QueueSheet(
                    visible = true,
                    queue = PlaybackQueueSnapshot(order, currentIndex = current.intValue),
                    shuffleEnabled = true,
                    isPlaying = false,
                    tracksById = tracks,
                    artwork = { Box(Modifier.size(44.dp).background(Color(0xFF536274))) },
                    onDismiss = {},
                    onSelectPosition = {},
                )
            }
        }
        rule.onNodeWithText("51 / 100").assertExists()
        rule.onNodeWithTag("queue-track-49").assertIsDisplayed()
        rule.onNodeWithText("Перемешивание включено").assertExists()
        rule.runOnIdle { current.intValue = 70 }
        rule.onNodeWithText("71 / 100").assertExists()
        rule.onNodeWithTag("queue-track-49").assertIsDisplayed()
        rule.onNodeWithTag("queue-track-70").assertDoesNotExist()
    }

    @Test fun systemBackDismissesTheQueueSheet() {
        var dismissed = 0
        val visible = mutableStateOf(true)
        rule.setContent {
            EnoTheme(darkTheme = true) {
                QueueSheet(
                    visible = visible.value,
                    queue = PlaybackQueueSnapshot(ids.take(8), currentIndex = 2),
                    shuffleEnabled = false,
                    isPlaying = false,
                    tracksById = tracks,
                    artwork = { Box(Modifier.size(44.dp).background(Color(0xFF536274))) },
                    onDismiss = { dismissed++; visible.value = false },
                    onSelectPosition = {},
                )
            }
        }
        rule.onNodeWithText("Очередь").assertExists()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test fun regularQueueGraphite() = screenshot("regular", count = 9, current = 3, dark = true)

    @Test fun regularQueuePorcelain() = screenshot("regular", count = 9, current = 3, dark = false)

    @Test fun shuffleQueueGraphite() = screenshot("shuffle", count = 12, current = 4, shuffle = true, dark = true)

    @Test fun shuffleQueuePorcelain() = screenshot("shuffle", count = 12, current = 4, shuffle = true, dark = false)

    @Test fun middleLongQueueGraphite() = screenshot("middle_long", count = 1_000, current = 500, dark = true)

    @Test fun middleLongQueuePorcelain() = screenshot("middle_long", count = 1_000, current = 500, dark = false)

    private fun screenshot(
        variant: String,
        count: Int,
        current: Int,
        shuffle: Boolean = false,
        dark: Boolean,
    ) {
        val order = ids.take(count).let { values ->
            if (shuffle) values.drop(5) + values.take(5) else values
        }
        rule.setContent {
            EnoTheme(darkTheme = dark) {
                QueueSheet(
                    visible = true,
                    queue = PlaybackQueueSnapshot(order, current),
                    shuffleEnabled = shuffle,
                    isPlaying = true,
                    tracksById = tracks,
                    artwork = { Box(Modifier.size(44.dp).background(Color(0xFF536274))) },
                    onDismiss = {},
                    onSelectPosition = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(
            "src/test/screenshots/queue_${variant}_${if (dark) "graphite" else "porcelain"}.png",
        )
    }

    private fun trackId(index: Int) = "queue-${index.toString().padStart(3, '0')}"
}
