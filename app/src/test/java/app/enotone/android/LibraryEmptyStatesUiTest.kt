package app.enotone.android

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.component.EnoBottomBar
import app.enotone.android.core.ui.component.EnoBottomBarItem
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.android.core.ui.component.EnoMiniPlayer
import app.enotone.android.core.ui.component.EnoMiniPlayerLabels
import app.enotone.android.core.ui.theme.EnoToneTheme
import app.enotone.library.LibraryPage
import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineTrackRow
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w377dp-h816dp-xxxhdpi")
class LibraryEmptyStatesUiTest {
    @get:Rule val rule = createComposeRule()

    private val track = OfflineTrackRow(
        id = "synthetic-track",
        title = "Вымышленная песня",
        artist = "Вымышленный исполнитель",
        album = "Вымышленный альбом",
    )
    private val group = OfflineLibraryGroup(
        key = "synthetic-group",
        name = "Вымышленный альбом",
        subtitle = "Вымышленный исполнитель",
        trackIds = setOf(track.id),
    )

    @Test
    fun emptySearchStateAppearsOnEverySearchPageAndDisappearsWithResults() {
        var state by mutableStateOf(searchState(LibraryPage.TRACKS, hasResults = false))
        rule.setContent {
            Screen(dark = true) {
                Library(state = state)
            }
        }

        for (page in LibraryPage.values()) {
            rule.runOnIdle { state = searchState(page, hasResults = false) }
            rule.waitForIdle()
            rule.onAllNodesWithText("Ничего не нашлось").assertCountEquals(1)
            rule.onNodeWithText(
                "Попробуйте другое написание — я ищу по названию, исполнителю и альбому.",
            ).assertIsDisplayed()
            rule.onAllNodesWithText("Проверить подключение").assertCountEquals(0)

            rule.runOnIdle { state = searchState(page, hasResults = true) }
            rule.waitForIdle()
            rule.onAllNodesWithText("Ничего не нашлось").assertCountEquals(0)
        }

        rule.runOnIdle {
            state = searchState(LibraryPage.TRACKS, hasResults = false).copy(
                tracks = emptyList(),
                groups = emptyList(),
                managedScanStatus = ManagedScanStatus(ManagedScanState.COMPLETE),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithText("Ничего не нашлось").assertIsDisplayed()
        rule.onAllNodesWithText("Где же музыка?").assertCountEquals(0)
    }

    @Test
    fun musicMissingStateWaitsUntilIndexingCompletes() {
        var state by mutableStateOf(missingLibraryState().copy(
            managedScanStatus = ManagedScanStatus(ManagedScanState.RUNNING),
        ))
        rule.setContent { Screen(dark = true) { Library(state = state) } }

        rule.onAllNodesWithText("Где же музыка?").assertCountEquals(0)
        rule.runOnIdle { state = state.copy(managedScanStatus = ManagedScanStatus(ManagedScanState.COMPLETE)) }
        rule.waitForIdle()
        rule.onNodeWithText("Где же музыка?").assertIsDisplayed()
    }

    @Test
    fun emptyLibraryActionOpensSettings() {
        var openedSettings = 0
        rule.setContent {
            Screen(dark = false) {
                Library(
                    state = missingLibraryState(),
                    onOpenSettings = { openedSettings++ },
                )
            }
        }

        rule.onNodeWithText("Где же музыка?").assertIsDisplayed()
        rule.onNodeWithText("В папке /AllMusic нет файлов MP3 или FLAC.").assertIsDisplayed()
        rule.onNodeWithText("Проверить подключение").performClick()
        rule.runOnIdle { assertEquals(1, openedSettings) }
    }

    @Test
    fun miniPlayerIsHiddenOnlyOnTheEmptyLibraryTab() {
        val empty = missingLibraryState()
        assertFalse(appMiniPlayerVisible(AppNavState(tab = AppTab.Library(LibraryPage.TRACKS)), empty, 1))
        assertTrue(appMiniPlayerVisible(AppNavState(tab = AppTab.Home), empty, 1))
        assertTrue(appMiniPlayerVisible(
            AppNavState(tab = AppTab.Library(LibraryPage.TRACKS)), empty.copy(loading = true), 1,
        ))
        assertTrue(appMiniPlayerVisible(
            AppNavState(tab = AppTab.Library(LibraryPage.TRACKS)), empty.copy(search = "нет совпадений"), 1,
        ))
        assertTrue(appMiniPlayerVisible(
            AppNavState(tab = AppTab.Library(LibraryPage.TRACKS)),
            empty.copy(managedScanStatus = ManagedScanStatus(ManagedScanState.RUNNING)),
            1,
        ))
        assertFalse(appMiniPlayerVisible(AppNavState(tab = AppTab.Home), empty, 0))
    }

    @Test fun notFoundGraphite() = captureNotFound(dark = true)

    @Test fun notFoundPorcelain() = captureNotFound(dark = false)

    @Test fun musicMissingGraphite() = captureMusicMissing(dark = true)

    @Test fun musicMissingPorcelain() = captureMusicMissing(dark = false)

    private fun captureNotFound(dark: Boolean) {
        rule.setContent {
            Screen(dark) {
                Box(Modifier.fillMaxSize()) {
                    Library(
                        state = searchState(LibraryPage.TRACKS, hasResults = false),
                        contentPadding = dockPadding(withMiniPlayer = true),
                    )
                    Dock(withMiniPlayer = true)
                }
            }
        }
        rule.onNodeWithText("не найдено").assertIsDisplayed()
        rule.onNodeWithText("Песни").assertIsDisplayed()
        rule.onNodeWithText("Вымышленная песня").assertIsDisplayed()
        capture("d6a_not_found_${theme(dark)}")
    }

    private fun captureMusicMissing(dark: Boolean) {
        rule.setContent {
            Screen(dark) {
                Box(Modifier.fillMaxSize()) {
                    Library(
                        state = missingLibraryState(),
                        contentPadding = dockPadding(withMiniPlayer = false),
                    )
                    Dock(withMiniPlayer = false)
                }
            }
        }
        rule.onNodeWithText("Песни").assertIsDisplayed()
        rule.onAllNodesWithText("Вымышленная песня").assertCountEquals(0)
        capture("d6a_music_missing_${theme(dark)}")
    }

    @Composable
    private fun Library(
        state: LibraryUiState,
        contentPadding: PaddingValues = PaddingValues(),
        onOpenSettings: () -> Unit = {},
    ) {
        val context = LocalContext.current
        LibraryScreenContent(
            state = state,
            actions = PreviewLibraryActions,
            artworkRepository = remember(context) { ArtworkPreviewRepository(context) },
            accountEmail = "",
            appPassword = "",
            contentPadding = contentPadding,
            onOpenSettings = onOpenSettings,
        )
    }

    @Composable
    private fun BoxScope.Dock(withMiniPlayer: Boolean) {
        val labels = listOf(
            EnoBottomBarItem(EnoIcons.HOME, stringResource(R.string.nav_home)),
            EnoBottomBarItem(EnoIcons.SONGS, stringResource(R.string.nav_songs)),
            EnoBottomBarItem(EnoIcons.ALBUMS, stringResource(R.string.nav_albums)),
            EnoBottomBarItem(EnoIcons.ARTISTS, stringResource(R.string.nav_artists)),
            EnoBottomBarItem(EnoIcons.ALBUM_ARTISTS, stringResource(R.string.nav_album_artists)),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (withMiniPlayer) {
                EnoMiniPlayer(
                    title = "Вымышленная песня",
                    subtitle = "Вымышленный исполнитель",
                    isPlaying = true,
                    progress = 0.4f,
                    labels = EnoMiniPlayerLabels("Открыть", "Играть", "Пауза", "Следующая"),
                    onOpen = {},
                    onPlayPause = {},
                    onNext = {},
                )
            }
            EnoBottomBar(items = labels, selectedIndex = 1, onSelect = {})
        }
    }

    @Composable
    private fun Screen(dark: Boolean, content: @Composable () -> Unit) {
        EnoToneTheme(darkTheme = dark) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                content = content,
            )
        }
    }

    @Composable
    private fun dockPadding(withMiniPlayer: Boolean): PaddingValues {
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val dockHeight = 70.dp + 12.dp + 16.dp + if (withMiniPlayer) 64.dp + 8.dp else 0.dp
        return PaddingValues(bottom = dockHeight + bottomInset)
    }

    private fun capture(name: String) =
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    private fun theme(dark: Boolean) = if (dark) "graphite" else "porcelain"

    private fun searchState(page: LibraryPage, hasResults: Boolean) = LibraryUiState(
        section = page,
        search = "не найдено",
        loading = false,
        tracks = listOf(track),
        visible = if (page == LibraryPage.TRACKS && hasResults) listOf(track) else emptyList(),
        groups = listOf(group),
        shownGroups = if (page != LibraryPage.TRACKS && hasResults) listOf(group) else emptyList(),
    )

    private fun missingLibraryState() = LibraryUiState(
        loading = false,
        managedScanStatus = ManagedScanStatus(state = ManagedScanState.COMPLETE),
    )
}
