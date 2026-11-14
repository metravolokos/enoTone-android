package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.enotone.android.core.ui.component.EnoBlurState
import app.enotone.android.core.ui.component.EnoBottomBar
import app.enotone.android.core.ui.component.EnoBottomBarItem
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.android.core.ui.component.EnoMiniPlayer
import app.enotone.android.core.ui.component.EnoMiniPlayerLabels
import app.enotone.android.core.ui.component.LocalEnoDockBlur
import app.enotone.android.core.ui.component.rememberEnoBlurState
import app.enotone.library.LibraryPage
import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineLibraryGrouping
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
@Config(sdk = [35], qualifiers = "ru-rRU-w377dp-h816dp-xxxhdpi")
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
    private fun Library(state: LibraryUiState, blur: EnoBlurState? = null, bottom: Dp = 24.dp) {
        val context = LocalContext.current
        LibraryScreenContent(
            state = state,
            actions = PreviewLibraryActions,
            artworkRepository = remember(context) { ArtworkPreviewRepository(context) },
            accountEmail = "",
            appPassword = "x",
            contentPadding = PaddingValues(bottom = bottom),
            blur = blur,
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

    /** Как в приложении: тема и Surface фона (обёртка EnoToneTheme в :app), чтобы цвет текста был темы. */
    @Composable
    private fun Screen(dark: Boolean, content: @Composable () -> Unit) {
        EnoTheme(dark) { Surface(color = MaterialTheme.colorScheme.background, content = content) }
    }

    private fun capture(name: String) = rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    @Test fun songsGraphite() = songs(dark = true)

    @Test fun songsPorcelain() = songs(dark = false)

    private fun songs(dark: Boolean) {
        rule.setContent { Screen(dark) { Library(songsState()) } }
        capture("songs_${theme(dark)}")
    }

    @Test fun songsScrolledGraphite() = songsScrolled(dark = true)

    @Test fun songsScrolledPorcelain() = songsScrolled(dark = false)

    private fun songsScrolled(dark: Boolean) {
        rule.setContent { Screen(dark) { Library(songsState()) } }
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(SCROLL_TO)
        rule.waitForIdle()
        capture("songs_scrolled_${theme(dark)}")
    }

    @Test fun albumsScrolledGraphite() = albumsScrolled(dark = true)

    @Test fun albumsScrolledPorcelain() = albumsScrolled(dark = false)

    /** D3.2: заголовок вкладки закреплён, сетка уходит под него (размытие и линия). */
    private fun albumsScrolled(dark: Boolean) {
        rule.setContent { Screen(dark) { Library(albumsState()) } }
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(ALBUMS_SCROLL_TO)
        rule.waitForIdle()
        capture("albums_scrolled_${theme(dark)}")
    }

    @Test fun translucentDockGraphite() = translucentDock(dark = true)

    @Test fun translucentDockPorcelain() = translucentDock(dark = false)

    /** D3.2/D3.3: «Прозрачные панели» включены — заголовок, мини-плеер и панель над прокрученным списком. */
    private fun translucentDock(dark: Boolean) {
        rule.setContent {
            Screen(dark) {
                val blur = rememberEnoBlurState()
                // D3.3: настройка включает размытие и верхнего заголовка, и нижних панелей.
                CompositionLocalProvider(LocalEnoDockBlur provides blur) {
                    Box(Modifier.fillMaxSize()) {
                        Library(albumsState(), blur = blur, bottom = DOCK_SPACE)
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EnoMiniPlayer(
                                title = "Вымышленная песня", subtitle = "Выдуманный артист",
                                isPlaying = true, progress = 0.4f,
                                labels = EnoMiniPlayerLabels("open", "play", "pause", "next"),
                                onOpen = {}, onPlayPause = {}, onNext = {},
                            )
                            EnoBottomBar(
                                items = listOf(
                                    EnoBottomBarItem(EnoIcons.HOME, "Домой"),
                                    EnoBottomBarItem(EnoIcons.SONGS, "Песни"),
                                    EnoBottomBarItem(EnoIcons.ALBUMS, "Альбомы"),
                                    EnoBottomBarItem(EnoIcons.ARTISTS, "Артисты"),
                                    EnoBottomBarItem(EnoIcons.ALBUM_ARTISTS, "Авторы"),
                                ),
                                selectedIndex = 2, onSelect = {},
                            )
                        }
                    }
                }
            }
        }
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(ALBUMS_SCROLL_TO)
        rule.waitForIdle()
        capture("translucent_dock_${theme(dark)}")
    }

    @Test fun settingsAppearanceGraphite() = settingsAppearance(dark = true)

    @Test fun settingsAppearancePorcelain() = settingsAppearance(dark = false)

    /** D3.2: переключатель «Прозрачные панели» в «Оформлении» (включён для наглядности). */
    private fun settingsAppearance(dark: Boolean) {
        rule.setContent {
            Screen(dark) {
                AppearanceSettingsContent(UiMode.SYSTEM, {}, translucentDock = true, onTranslucentDock = {})
            }
        }
        capture("settings_appearance_${theme(dark)}")
    }

    @Test fun albumsGraphite() = albums(dark = true)

    @Test fun albumsPorcelain() = albums(dark = false)

    private fun albums(dark: Boolean) {
        rule.setContent { Screen(dark) { Library(albumsState()) } }
        capture("albums_${theme(dark)}")
    }

    @Test fun homeGraphite() = home(dark = true)

    @Test fun homePorcelain() = home(dark = false)

    private fun home(dark: Boolean) {
        rule.setContent {
            Screen(dark) {
                EnoToneHomeScreen(connectedAccount = true, songCount = 3590, onSettings = {}, onShuffleAll = {})
            }
        }
        capture("home_${theme(dark)}")
    }

    @Test fun nowPlayingGraphite() = nowPlaying(dark = true)

    @Test fun nowPlayingPorcelain() = nowPlaying(dark = false)

    private fun nowPlaying(dark: Boolean) {
        rule.setContent {
            Screen(dark) {
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


    @Test fun songActionsGraphite() = songActions(true)

    @Test fun songActionsPorcelain() = songActions(false)

    private fun songActions(dark: Boolean) {
        rule.setContent { Screen(dark) { Library(songsState()) } }
        rule.onNodeWithContentDescription("Действия: Вымышленная песня 1").performClick()
        rule.waitForIdle()
        capture("song_actions_${theme(dark)}")
    }

    @Test fun artistTopGraphite() = artistTop(true)

    @Test fun artistTopPorcelain() = artistTop(false)

    @Test fun artistScrolledGraphite() = artistScrolled(true)

    @Test fun artistScrolledPorcelain() = artistScrolled(false)

    @Test fun artistCompilationsGraphite() = artistCompilations(true)

    @Test fun artistCompilationsPorcelain() = artistCompilations(false)

    @Composable
    private fun ArtistScreen(compilations: Boolean = false) {
        val context = LocalContext.current
        val rows = List(if (compilations) 5 else 22) { index ->
            OfflineTrackRow(
                id = "artist-$index",
                title = "Вымышленная песня ${index + 1}",
                artist = "Выдуманный артист",
                album = if (compilations) "Сборник" else "Придуманный альбом ${index / 8 + 1}",
                albumArtist = if (compilations) "Разные исполнители" else "Выдуманный артист",
                year = if (compilations) 2022 else 2024 - index / 8,
                trackNumber = index % 8 + 1,
                hasStoredTagData = true,
            )
        }
        val performer = OfflineLibraryGrouping.artists(rows).single()
        ArtistDetailScreen(
            state = LibraryUiState(
                loading = false, section = LibraryPage.ARTISTS, tracks = rows,
                selectedGroup = performer, selectedGroupKey = performer.key,
                playbackConnected = true,
            ),
            artist = performer,
            actions = PreviewLibraryActions,
            artworkRepository = remember(context) { ArtworkPreviewRepository(context) },
            accountEmail = "", appPassword = "",
            contentPadding = PaddingValues(bottom = 186.dp),
            onOpenDetail = {}, onBackDetail = {},
        )
    }

    private fun artistTop(dark: Boolean) {
        rule.setContent { Screen(dark) { ArtistScreen() } }
        capture("artist_top_${theme(dark)}")
    }

    private fun artistScrolled(dark: Boolean) {
        rule.setContent { Screen(dark) { ArtistScreen() } }
        rule.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(8)
        rule.waitForIdle()
        capture("artist_scrolled_${theme(dark)}")
    }

    private fun artistCompilations(dark: Boolean) {
        rule.setContent { Screen(dark) { ArtistScreen(compilations = true) } }
        capture("artist_compilations_${theme(dark)}")
    }

    private fun theme(dark: Boolean) = if (dark) "graphite" else "porcelain"

    private companion object {
        const val SAMPLE_TRACKS = 24
        const val SAMPLE_ALBUMS = 8
        const val SCROLL_TO = 8
        const val ALBUMS_SCROLL_TO = 2
        val DOCK_SPACE = 186.dp
    }
}
