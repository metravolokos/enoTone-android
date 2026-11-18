package app.enotone.android

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineTrackRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecentlyAddedUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private class RecordingActions : LibraryScreenActions by PreviewLibraryActions {
        var selected: OfflineTrackRow? = null
        var selectedContext: List<OfflineTrackRow> = emptyList()
        var shuffledContext: List<OfflineTrackRow> = emptyList()

        override fun onTrackClicked(track: OfflineTrackRow, context: List<OfflineTrackRow>) {
            selected = track
            selectedContext = context
        }

        override fun onShuffleTracks(track: OfflineTrackRow, context: List<OfflineTrackRow>) {
            selected = track
            shuffledContext = context
        }
    }

    private val tracks = (1..120).map { index ->
        OfflineTrackRow(
            id = "track-$index",
            title = "Track $index",
            artist = "Artist $index",
            album = "Album $index",
            firstSeenEpochMs = index.toLong(),
        )
    }
    private val albums = (1..55).map { index ->
        OfflineLibraryGroup(
            key = "album:$index",
            name = "Recent release $index",
            subtitle = "Artist $index",
            trackIds = setOf("track-$index"),
        )
    }
    private val libraryState = LibraryUiState(
        tracks = tracks,
        recentlyAddedAlbums = albums,
        recentlyAddedTracks = tracks.reversed().take(100),
        loading = false,
        playbackConnected = true,
    )

    @Test fun homeShowsSectionsAndAllOpensTheirDetailRoutes() {
        var state = AppNavState()
        rule.setContent {
            EnoToneTheme {
                EnoToneHomeScreen(
                    connectedAccount = true,
                    songCount = 120,
                    onSettings = {},
                    onShuffleAll = {},
                    recentContent = {
                        RecentlyAddedHomeSections(
                            state = libraryState,
                            actions = PreviewLibraryActions,
                            artworkRepository = ArtworkPreviewRepository(context),
                            accountEmail = "",
                            appPassword = "",
                            onOpenDetail = { state = AppNavigator.push(state, it) },
                        )
                    },
                )
            }
        }
        rule.onNodeWithText(context.getString(R.string.home_recently_added_albums)).assertExists()
        rule.onNodeWithText(context.getString(R.string.home_recently_added_songs)).assertExists()
        rule.onAllNodesWithText(context.getString(R.string.home_recently_added_all))[0].performClick()
        rule.runOnIdle {
            assertEquals(
                LibraryDetail.RecentlyAdded(RecentlyAddedKind.ALBUMS),
                state.details.last(),
            )
        }
        rule.runOnIdle { state = AppNavigator.back(state) ?: state }
        rule.onAllNodesWithText(context.getString(R.string.home_recently_added_all))[1].performClick()
        rule.runOnIdle {
            assertEquals(
                LibraryDetail.RecentlyAdded(RecentlyAddedKind.TRACKS),
                state.details.last(),
            )
        }
    }

    @Test fun homeSongTapUsesTheFiveVisibleRecentlyAddedTracksAndMoreOpensActions() {
        val actions = RecordingActions()
        val recentTracks = tracks.reversed().take(5)
        rule.setContent {
            EnoToneTheme {
                RecentlyAddedHomeSections(
                    state = libraryState.copy(recentlyAddedTracks = tracks.reversed().take(5)),
                    actions = actions,
                    artworkRepository = ArtworkPreviewRepository(context),
                    accountEmail = "",
                    appPassword = "",
                    onOpenDetail = {},
                )
            }
        }
        val firstTrack = recentTracks.first()
        rule.onNodeWithText(firstTrack.title).performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(firstTrack.id, actions.selected?.id)
            assertEquals(5, actions.selectedContext.size)
            assertEquals(recentTracks.map { it.id }, actions.selectedContext.map { it.id })
        }
        rule.onNodeWithContentDescription(
            context.getString(R.string.song_actions_description, firstTrack.title),
        ).performScrollTo().performClick()
        rule.onNodeWithText(context.getString(R.string.queue_play_next)).assertExists()
    }

    @Test fun homeAlbumTapOpensItOverHomeAndBackReturnsHome() {
        var nav = AppNavState()
        rule.setContent {
            EnoToneTheme {
                RecentlyAddedHomeSections(
                    state = libraryState,
                    actions = PreviewLibraryActions,
                    artworkRepository = ArtworkPreviewRepository(context),
                    accountEmail = "",
                    appPassword = "",
                    onOpenDetail = { nav = AppNavigator.push(nav, it) },
                )
            }
        }
        rule.onNodeWithText("Recent release 1").performClick()
        rule.runOnIdle {
            assertEquals(LibraryDetail.Album("album:1"), nav.details.last())
            nav = AppNavigator.back(nav) ?: nav
            assertEquals(AppNavState(), nav)
        }
    }

    @Test fun allSongsPlayAndShuffleUseTheCappedHundredTrackContext() {
        val actions = RecordingActions()
        rule.setContent {
            EnoToneTheme {
                RecentlyAddedScreen(
                    kind = RecentlyAddedKind.TRACKS,
                    state = libraryState,
                    actions = actions,
                    artworkRepository = ArtworkPreviewRepository(context),
                    accountEmail = "",
                    appPassword = "connected-password",
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                    onOpenDetail = {},
                )
            }
        }
        rule.onNodeWithText(context.getString(R.string.ui_play)).performClick()
        rule.runOnIdle { assertEquals(100, actions.selectedContext.size) }
        rule.onNodeWithText(context.getString(R.string.library_shuffle_all)).performClick()
        rule.runOnIdle { assertEquals(100, actions.shuffledContext.size) }
    }

    @Test fun homeHidesEmptySectionsAndRetainsShuffleCard() {
        rule.setContent {
            EnoToneTheme {
                EnoToneHomeScreen(
                    connectedAccount = true,
                    songCount = 120,
                    onSettings = {},
                    onShuffleAll = {},
                    recentContent = {
                        RecentlyAddedHomeSections(
                            state = LibraryUiState(tracks = tracks, loading = false),
                            actions = PreviewLibraryActions,
                            artworkRepository = ArtworkPreviewRepository(context),
                            accountEmail = "",
                            appPassword = "",
                            onOpenDetail = {},
                        )
                    },
                )
            }
        }
        rule.onNodeWithText(context.getString(R.string.home_shuffle_all)).assertExists()
        rule.onNodeWithText(context.getString(R.string.home_recently_added_albums)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.home_recently_added_songs)).assertDoesNotExist()
    }
}
