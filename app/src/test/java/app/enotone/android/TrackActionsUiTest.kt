package app.enotone.android

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.enotone.library.LibraryPage
import app.enotone.library.OfflineTrackRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Поведенческие сценарии листа, без тестирования строк в исходниках. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w377dp-h816dp-xxxhdpi")
class TrackActionsUiTest {
    @get:Rule val rule = createComposeRule()

    private val track = OfflineTrackRow(
        id = "track-1", title = "Песня для теста",
        artist = "Вымышленный артист", album = "Придуманный альбом",
        albumArtist = "Вымышленный артист", hasStoredTagData = true,
    )

    @Test fun moreShowsBothTargetsAndAlbumOpensOverCurrentTab() {
        var route: LibraryDetail? = null
        rule.setContent {
            EnoToneTheme {
                val context = LocalContext.current
                LibraryScreenContent(
                    state = LibraryUiState(
                        loading = false, section = LibraryPage.TRACKS,
                        tracks = listOf(track), visible = listOf(track),
                        groupIndex = LibraryGroupIndex.build(listOf(track), "Неизвестный альбом"),
                    ),
                    actions = PreviewLibraryActions,
                    artworkRepository = remember(context) { ArtworkPreviewRepository(context) },
                    accountEmail = "", appPassword = "",
                    contentPadding = PaddingValues(bottom = 180.dp),
                    onOpenDetail = { route = it },
                )
            }
        }
        rule.onNodeWithContentDescription("Действия: Песня для теста").performClick()
        rule.onNodeWithText("Перейти к альбому").assertExists().performClick()
        rule.runOnIdle {
            assertEquals(LibraryDetail.Album("album:придуманный альбом\u0000вымышленный артист"), route)
        }
    }

    @Test fun longPressOpensSameSheet() {
        rule.setContent {
            EnoToneTheme {
                val context = LocalContext.current
                LibraryScreenContent(
                    state = LibraryUiState(
                        loading = false, section = LibraryPage.TRACKS,
                        tracks = listOf(track), visible = listOf(track),
                        groupIndex = LibraryGroupIndex.build(listOf(track), "Неизвестный альбом"),
                    ),
                    actions = PreviewLibraryActions,
                    artworkRepository = remember(context) { ArtworkPreviewRepository(context) },
                    accountEmail = "", appPassword = "",
                )
            }
        }
        rule.onNodeWithText("Песня для теста").performTouchInput { longClick() }
        rule.onNodeWithText("Перейти к исполнителю").assertExists()
    }

    @Test fun queueActionsDispatchSelectedSongAndDismissSheet() {
        var next: List<OfflineTrackRow> = emptyList()
        var append: List<OfflineTrackRow> = emptyList()
        val actions = object : LibraryScreenActions by PreviewLibraryActions {
            override fun onPlayNext(tracks: List<OfflineTrackRow>) { next = tracks }
            override fun onAppendToQueue(tracks: List<OfflineTrackRow>) { append = tracks }
        }
        rule.setContent {
            EnoToneTheme {
                val context = LocalContext.current
                LibraryScreenContent(
                    state = LibraryUiState(
                        loading = false, tracks = listOf(track), visible = listOf(track),
                        groupIndex = LibraryGroupIndex.build(listOf(track), "Неизвестный альбом"),
                    ),
                    actions = actions,
                    artworkRepository = remember(context) { ArtworkPreviewRepository(context) },
                    accountEmail = "", appPassword = "",
                )
            }
        }
        rule.onNodeWithContentDescription("Действия: Песня для теста").performClick()
        rule.onNodeWithText("Играть следующей").performClick()
        rule.runOnIdle { assertEquals(listOf(track), next) }
        rule.onNodeWithText("В конец очереди").assertDoesNotExist()
        rule.onNodeWithContentDescription("Действия: Песня для теста").performClick()
        rule.onNodeWithText("В конец очереди").performClick()
        rule.runOnIdle { assertEquals(listOf(track), append) }
    }

    @Test fun artistMenuUsesScreenOrderAndOmitsNavigation() {
        val second = track.copy(id = "track-2", title = "Вторая", trackNumber = 1)
        val rows = listOf(track.copy(trackNumber = 2), second)
        val index = LibraryGroupIndex.build(rows, "Неизвестный альбом")
        val artist = index.artists.single()
        val plan = ArtistDetailPlanner.plan(artist, index)
        var next: List<OfflineTrackRow> = emptyList()
        val actions = object : LibraryScreenActions by PreviewLibraryActions {
            override fun onPlayNext(tracks: List<OfflineTrackRow>) { next = tracks }
        }
        rule.setContent {
            EnoToneTheme {
                val context = LocalContext.current
                ArtistDetailScreen(
                    state = LibraryUiState(
                        loading = false, tracks = rows, groupIndex = index,
                        artistPlans = mapOf(artist.key to plan), playbackConnected = true,
                    ),
                    artist = artist, actions = actions,
                    artworkRepository = remember(context) { ArtworkPreviewRepository(context) },
                    accountEmail = "", appPassword = "x", contentPadding = PaddingValues(),
                    onOpenDetail = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Действия исполнителя").performClick()
        rule.onNodeWithText("Перейти к альбому").assertDoesNotExist()
        rule.onNodeWithText("Играть следующими").performClick()
        rule.runOnIdle { assertEquals(listOf("track-2", "track-1"), next.map { it.id }) }
    }

    @Test fun albumMenuAppendsAllSongsInAlbumOrder() {
        val second = track.copy(id = "track-2", title = "Вторая", trackNumber = 1)
        val rows = listOf(track.copy(trackNumber = 2), second)
        val index = LibraryGroupIndex.build(rows, "Неизвестный альбом")
        val album = index.albums.single()
        val ordered = listOf(second, rows.first())
        var appended: List<OfflineTrackRow> = emptyList()
        val actions = object : LibraryScreenActions by PreviewLibraryActions {
            override fun onAppendToQueue(tracks: List<OfflineTrackRow>) { appended = tracks }
        }
        rule.setContent {
            EnoToneTheme {
                val context = LocalContext.current
                LibraryScreenContent(
                    state = LibraryUiState(
                        loading = false, section = LibraryPage.ALBUMS, tracks = rows,
                        groupIndex = index, selectedGroup = album, selectedGroupKey = album.key,
                        albumTracks = ordered, visible = ordered, playbackConnected = true,
                    ),
                    actions = actions,
                    artworkRepository = remember(context) { ArtworkPreviewRepository(context) },
                    accountEmail = "", appPassword = "",
                )
            }
        }
        rule.onNodeWithContentDescription("Действия с альбомом").performClick()
        rule.onNodeWithText("Перейти к альбому").assertDoesNotExist()
        rule.onNodeWithText("В конец очереди").performClick()
        rule.runOnIdle { assertEquals(ordered, appended) }
    }
}
