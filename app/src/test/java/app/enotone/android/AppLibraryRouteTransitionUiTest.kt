package app.enotone.android

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import app.enotone.library.LibraryPage
import app.enotone.library.OfflineLibraryGroup
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
        val fixture = artistFixture()
        var nav by mutableStateOf(fixture.detail)
        var live by mutableStateOf(fixture.detailState)
        val snapshots = AppLibraryRouteSnapshots()
        rule.mainClock.autoAdvance = false
        rule.setContent { RenderArtistTransition(fixture, nav, live, snapshots) }
        rule.mainClock.advanceTimeByFrame()
        rule.onAllNodesWithTag("artist-detail-header").assertCountEquals(1)
        rule.runOnIdle {
            nav = fixture.root
            live = fixture.rootState
        }
        rule.mainClock.advanceTimeBy(125)
        rule.onAllNodesWithTag("artist-detail-header").assertCountEquals(1)
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onAllNodesWithTag("artist-detail-header").assertCountEquals(0)
    }

    private data class ArtistFixture(
        val artist: OfflineLibraryGroup,
        val root: AppNavState,
        val detail: AppNavState,
        val detailState: LibraryUiState,
        val rootState: LibraryUiState,
    )

    private fun artistFixture(): ArtistFixture {
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
        return ArtistFixture(artist, root, detail, detailState, rootState)
    }

    @Composable
    private fun RenderArtistTransition(
        fixture: ArtistFixture,
        nav: AppNavState,
        live: LibraryUiState,
        snapshots: AppLibraryRouteSnapshots,
    ) {
        val context = LocalContext.current
        val art = remember { ArtworkPreviewRepository(context) }
        EnoToneTheme {
            AppLibraryRouteTransition(
                nav = nav,
                liveState = live,
                snapshots = snapshots,
                searchRegistry = null,
            ) { route, frozen, _ ->
                val state = requireNotNull(frozen)
                if (route.details.lastOrNull() is LibraryDetail.Artist &&
                    state.selectedGroup?.key == fixture.artist.key) {
                    ArtistDetailScreen(
                        state = state,
                        artist = fixture.artist,
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
}
