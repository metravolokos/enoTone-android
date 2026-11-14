package app.enotone.android

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.enotone.library.LibraryPage
import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineTrackRow
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.enotone.android.core.ui.theme.EnoToneTheme as EnoTheme

/**
 * Экраны D3.1 на эталонном размере Huawei Nova 15 Pro (377 × 816 dp) в обеих темах — для сверки
 * с PNG-макетами `enotone-design/assets/ui-screens/android-v2/` (02, 04, 09, 10, 01).
 * Только выдуманные названия (AGENTS §5).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w377dp-h816dp-xxxhdpi")
class AppScreensScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val tracks = List(SAMPLE_TRACKS) { index ->
        OfflineTrackRow(
            id = "t$index",
            title = if (index == 1) "Очень длинное вымышленное название песни"
            else "Вымышленная песня ${index + 1}",
            artist = "Выдуманный артист",
            album = "Придуманный альбом",
        )
    }
    private val albums = List(SAMPLE_ALBUMS) { index ->
        OfflineLibraryGroup("a$index", "Альбом ${index + 1}", "Выдуманный артист", setOf("t$index"))
    }

    @Composable
    private fun Library(state: LibraryUiState) {
        val context = LocalContext.current
        LibraryScreenContent(
            state = state,
            actions = PreviewLibraryActions,
            artworkRepository = remember(context) { ArtworkPreviewRepository(context) },
            accountEmail = "",
            appPassword = "x",
            contentPadding = PaddingValues(bottom = 24.dp),
        )
    }

    private fun songsState() = LibraryUiState(
        loading = false, section = LibraryPage.TRACKS, tracks = tracks, visible = tracks,
        playbackConnected = true,
    )

    private fun albumsState() = LibraryUiState(
        loading = false, section = LibraryPage.ALBUMS, tracks = tracks, groups = albums,
        shownGroups = albums, playbackConnected = true,
    )

    private fun capture(name: String) = rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    @Test fun songsGraphite() = songs(dark = true)

    @Test fun songsPorcelain() = songs(dark = false)

    private fun songs(dark: Boolean) {
        rule.setContent { EnoTheme(dark) { Library(songsState()) } }
        capture("songs_${theme(dark)}")
    }

    @Test fun songsScrolledGraphite() = songsScrolled(dark = true)

    @Test fun songsScrolledPorcelain() = songsScrolled(dark = false)

    private fun songsScrolled(dark: Boolean) {
        rule.setContent { EnoTheme(dark) { Library(songsState()) } }
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(SCROLL_TO)
        rule.waitForIdle()
        capture("songs_scrolled_${theme(dark)}")
    }

    @Test fun albumsGraphite() = albums(dark = true)

    @Test fun albumsPorcelain() = albums(dark = false)

    private fun albums(dark: Boolean) {
        rule.setContent { EnoTheme(dark) { Library(albumsState()) } }
        capture("albums_${theme(dark)}")
    }

    @Test fun homeGraphite() = home(dark = true)

    @Test fun homePorcelain() = home(dark = false)

    private fun home(dark: Boolean) {
        rule.setContent {
            EnoTheme(dark) {
                EnoToneHomeScreen(connectedAccount = true, songCount = 3590, onSettings = {}, onShuffleAll = {})
            }
        }
        capture("home_${theme(dark)}")
    }

    @Test fun nowPlayingGraphite() = nowPlaying(dark = true)

    @Test fun nowPlayingPorcelain() = nowPlaying(dark = false)

    private fun nowPlaying(dark: Boolean) {
        rule.setContent {
            EnoTheme(dark) {
                NowPlayingScreen(
                    trackId = "t0", title = "Вымышленная песня", artist = "Выдуманный артист",
                    album = "Придуманный альбом", artwork = null, playbackState = "PLAYING", errorCode = 0,
                    positionMs = 3_000L, durationMs = 200_000L, canSeek = true, controlsEnabled = true,
                    hasPrevious = true, hasNext = true, queuePosition = 34, queueTotal = 3647,
                    repeatMode = Player.REPEAT_MODE_ONE, shuffleEnabled = true,
                    onClose = {}, onSeekFraction = {}, onPlayPause = {}, onPrevious = {}, onNext = {},
                    onCycleRepeat = {}, onToggleShuffle = {},
                )
            }
        }
        capture("now_playing_${theme(dark)}")
    }

    private fun theme(dark: Boolean) = if (dark) "graphite" else "porcelain"

    private companion object {
        const val SAMPLE_TRACKS = 24
        const val SAMPLE_ALBUMS = 8
        const val SCROLL_TO = 8
    }
}
