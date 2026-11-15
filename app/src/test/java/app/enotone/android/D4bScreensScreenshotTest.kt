package app.enotone.android

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.enotone.library.LibraryPage
import app.enotone.library.OfflineTrackCatalog
import app.enotone.library.OfflineTrackRow
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.enotone.android.core.ui.theme.EnoToneTheme as EnoTheme

/** Эталоны D4b: экран 377×816 dp, только вымышленные названия, обе темы. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w377dp-h816dp-xxxhdpi")
class D4bScreensScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val longName = "Очень длинное вымышленное имя музыкального артиста"
    private val songs = listOf(
        song("a1", longName, "Придуманный альбом", longName, 183_000L),
        song("a2", longName, "Придуманный альбом", longName, null),
        song("c1", "Гость сборника", "Общий сборник", "VA", 200_000L),
        song("c2", "Другой гость", "Сборник без автора", null, null),
        song("c3", "Третий гость", "Сборник без автора", null, 154_000L),
    )

    private fun song(
        id: String, artist: String, album: String,
        albumArtist: String?, duration: Long?,
    ) = OfflineTrackRow(
        id = id,
        title = when (id) {
            "a1" -> "Первая песня"
            "a2" -> "Вторая песня"
            "c1" -> "Песня сборника"
            "c2" -> "Ещё песня"
            "c3" -> "Заключительная"
            else -> id
        },
        artist = artist, album = album,
        albumArtist = albumArtist, year = 2024,
        trackNumber = if (id == "a2" || id == "c3") 2 else 1,
        genre = "Инди", durationMs = duration, hasStoredTagData = true,
    )

    @Composable
    private fun Library(state: LibraryUiState) {
        val context = LocalContext.current
        LibraryScreenContent(
            state = state,
            actions = PreviewLibraryActions,
            artworkRepository = remember(context) { ArtworkPreviewRepository(context) },
            accountEmail = "", appPassword = "x",
            contentPadding = PaddingValues(bottom = 186.dp),
        )
    }

    private fun performers(page: LibraryPage, dark: Boolean) {
        val index = LibraryGroupIndex.build(songs, "Неизвестный альбом")
        val groups = if (page == LibraryPage.ARTISTS) index.artists else index.albumArtists
        val state = LibraryUiState(
            loading = false, section = page, tracks = songs,
            groupIndex = index, groups = groups, shownGroups = groups,
            artistPlans = index.artists.associate {
                it.key to ArtistDetailPlanner.plan(it, index)
            },
        )
        rule.setContent {
            EnoTheme(dark) {
                Surface(color = MaterialTheme.colorScheme.background) { Library(state) }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(
            "src/test/screenshots/d4b_${if (page == LibraryPage.ARTISTS) "artists" else "authors"}_${theme(dark)}.png",
        )
    }

    private fun album(dark: Boolean) {
        val tracks = OfflineTrackCatalog.albumOrder(songs.take(2))
        val index = LibraryGroupIndex.build(tracks, "Неизвестный альбом")
        val album = index.albums.single()
        val state = LibraryUiState(
            loading = false, section = LibraryPage.ALBUMS, tracks = tracks,
            groupIndex = index, selectedGroup = album, selectedGroupKey = album.key,
            albumTracks = tracks, visible = tracks,
            albumYear = 2024, albumGenre = "Инди",
            currentTrackId = tracks.first().id, playbackState = "PLAYING",
            playbackConnected = true,
        )
        rule.setContent {
            EnoTheme(dark) {
                Surface(color = MaterialTheme.colorScheme.background) { Library(state) }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/d4b_album_${theme(dark)}.png")
    }

    private fun theme(dark: Boolean) = if (dark) "graphite" else "porcelain"

    @Test fun artistsGraphite() = performers(LibraryPage.ARTISTS, true)

    @Test fun artistsPorcelain() = performers(LibraryPage.ARTISTS, false)

    @Test fun authorsGraphite() = performers(LibraryPage.ALBUM_ARTISTS, true)

    @Test fun authorsPorcelain() = performers(LibraryPage.ALBUM_ARTISTS, false)

    @Test fun albumGraphite() = album(true)

    @Test fun albumPorcelain() = album(false)
}
