package app.enotone.android

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import app.enotone.library.LibraryPage
import app.enotone.library.OfflineTrackRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Реальные AppLibraryRouteTransition и ArtistDetailScreen, не текстовая имитация шапки. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp")
class AppLibraryRouteTransitionUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun outgoingArtistKeepsItsOwnHeaderHalfwayThroughBackTransition() {
        val track = OfflineTrackRow(id = "one", title = "Song", artist = "Performer", album = "Record")
        val index = LibraryGroupIndex.build(listOf(track), "Unknown")
        val artist = index.artists.single()
        val root = AppNavState(tab = AppTab.Library(LibraryPage.ARTISTS))
        val detail = AppNavigator.push(root, LibraryDetail.Artist(artist.key))
        val detailState = LibraryUiState(
            section = LibraryPage.ARTISTS,
            selectedGroupKey = artist.key,
            derivedRoute = LibraryPage.ARTISTS to artist.key,
            selectedGroup = artist,
            groupIndex = index,
            tracks = listOf(track),
            loading = false,
        )
        val rootState = detailState.copy(
            selectedGroupKey = null,
            derivedRoute = LibraryPage.ARTISTS to null,
            selectedGroup = null,
            groups = index.artists,
            shownGroups = index.artists,
        )
        var nav by mutableStateOf(detail)
        var live by mutableStateOf(detailState)
        val snapshots = AppLibraryRouteSnapshots()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val context = LocalContext.current
            val art = remember { ArtworkPreviewRepository(context) }
            EnoToneTheme {
                AppLibraryRouteTransition(
                    nav = nav,
                    liveState = live,
                    snapshots = snapshots,
                    playerExpanded = false,
                    backProgress = 0f,
                    backPreviewRoute = null,
                    searchRegistry = null,
                ) { route, frozen, _ ->
                    val state = requireNotNull(frozen)
                    if (route.details.lastOrNull() is LibraryDetail.Artist &&
                        state.selectedGroup?.key == artist.key) {
                        ArtistDetailScreen(
                            state = state,
                            artist = artist,
                            actions = PreviewLibraryActions,
                            artworkRepository = art,
                            accountEmail = "",
                            appPassword = "",
                            contentPadding = PaddingValues(),
                            onOpenDetail = {},
                        )
                    } else {
                        LibraryScreenContent(
                            state = state,
                            actions = PreviewLibraryActions,
                            artworkRepository = art,
                            accountEmail = "",
                            appPassword = "",
                            contentPadding = PaddingValues(),
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onAllNodesWithTag("artist-detail-header").assertCountEquals(1)
        rule.runOnIdle {
            nav = root
            live = rootState // VM уже отдала новый маршрут, старый header нельзя подменить.
        }
        rule.mainClock.advanceTimeBy(125)
        rule.onAllNodesWithTag("artist-detail-header").assertCountEquals(1)
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onAllNodesWithTag("artist-detail-header").assertCountEquals(0)
    }
}
