package app.enotone.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.theme.EnoToneTheme
import app.enotone.library.OfflineTrackRow
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** D4c: макет 09_home и экраны «Все» в обеих темах, только синтетические строки. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w377dp-h816dp-xxxhdpi")
class RecentlyAddedScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val tracks = List(120) { index ->
        val number = index + 1
        OfflineTrackRow(
            id = "recent-$number",
            title = if (number == 2) "Вымышленная длинная песня с названием" else "Вымышленная песня $number",
            artist = "Выдуманный артист",
            album = "Придуманный альбом ${index / 2 + 1}",
            albumArtist = "Выдуманный артист",
            hasStoredTagData = true,
            firstSeenEpochMs = number.toLong(),
        )
    }
    private val groups = LibraryGroupIndex.build(tracks, "Неизвестный альбом")
    private val state = LibraryUiState(
        loading = false,
        tracks = tracks,
        recentlyAddedAlbums = RecentlyAddedPlanner.albums(groups.albums, groups.tracksById),
        recentlyAddedTracks = RecentlyAddedPlanner.tracks(tracks),
        groupIndex = groups,
        playbackConnected = true,
    )

    @Composable
    private fun Screen(dark: Boolean, content: @Composable () -> Unit) {
        EnoToneTheme(dark) {
            Surface(color = MaterialTheme.colorScheme.background, content = content)
        }
    }

    @Composable
    private fun Home(recent: Boolean) {
        val appContext = LocalContext.current
        val artwork = remember(appContext) { ArtworkPreviewRepository(appContext) }
        EnoToneHomeScreen(
            connectedAccount = true,
            songCount = tracks.size,
            onSettings = {},
            onShuffleAll = {},
            recentContent = {
                if (recent) {
                    RecentlyAddedHomeSections(
                        state = state.copy(
                            recentlyAddedAlbums = state.recentlyAddedAlbums.take(10),
                            recentlyAddedTracks = state.recentlyAddedTracks.take(5),
                        ),
                        actions = PreviewLibraryActions,
                        artworkRepository = artwork,
                        accountEmail = "",
                        appPassword = "",
                        onOpenDetail = {},
                    )
                } else {
                    RecentlyAddedHomeSections(
                        state = state.copy(
                            recentlyAddedAlbums = emptyList(),
                            recentlyAddedTracks = emptyList(),
                        ),
                        actions = PreviewLibraryActions,
                        artworkRepository = artwork,
                        accountEmail = "",
                        appPassword = "",
                        onOpenDetail = {},
                    )
                }
            },
        )
    }

    private fun capture(name: String) =
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    @Test fun homeWithSectionsGraphite() = home(true, dark = true)

    @Test fun homeWithSectionsPorcelain() = home(true, dark = false)

    @Test fun homeWithoutSectionsGraphite() = home(false, dark = true)

    @Test fun homeWithoutSectionsPorcelain() = home(false, dark = false)

    private fun home(recent: Boolean, dark: Boolean) {
        rule.setContent { Screen(dark) { Home(recent) } }
        val contents = if (recent) "both" else "empty"
        capture("home_recent_${contents}_${theme(dark)}")
    }

    @Test fun allAlbumsGraphite() = allAlbums(dark = true)

    @Test fun allAlbumsPorcelain() = allAlbums(dark = false)

    private fun allAlbums(dark: Boolean) {
        rule.setContent {
            Screen(dark) {
                val appContext = LocalContext.current
                RecentlyAddedScreen(
                    kind = RecentlyAddedKind.ALBUMS,
                    state = state,
                    actions = PreviewLibraryActions,
                    artworkRepository = remember(appContext) { ArtworkPreviewRepository(appContext) },
                    accountEmail = "",
                    appPassword = "",
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 186.dp),
                    onOpenDetail = {},
                )
            }
        }
        capture("home_recent_all_albums_${theme(dark)}")
    }

    @Test fun allSongsGraphite() = allSongs(dark = true)

    @Test fun allSongsPorcelain() = allSongs(dark = false)

    private fun allSongs(dark: Boolean) {
        rule.setContent {
            Screen(dark) {
                val appContext = LocalContext.current
                RecentlyAddedScreen(
                    kind = RecentlyAddedKind.TRACKS,
                    state = state,
                    actions = PreviewLibraryActions,
                    artworkRepository = remember(appContext) { ArtworkPreviewRepository(appContext) },
                    accountEmail = "",
                    appPassword = "",
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 186.dp),
                    onOpenDetail = {},
                )
            }
        }
        capture("home_recent_all_songs_${theme(dark)}")
    }

    private fun theme(dark: Boolean) = if (dark) "graphite" else "porcelain"
}
