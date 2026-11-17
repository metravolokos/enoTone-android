package app.enotone.android

import android.graphics.Bitmap
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.enotone.library.AlbumTagRefreshReport
import app.enotone.library.LibraryPage
import app.enotone.library.LibraryRepository
import app.enotone.library.LibrarySnapshot
import app.enotone.library.OfflineTrackRow
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.QueueEditResult
import app.enotone.playback.QueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the actual player action sheet, route stack and player overlay together. */
@RunWith(RobolectricTestRunner::class)
@Config(application = EnoToneApplication::class, sdk = [33], qualifiers = "w393dp-h852dp")
class NowPlayingActionNavigationUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun albumActionClosesPlayerAndBackReturnsToPreviousTab() {
        val track = OfflineTrackRow(
            id = "fixture-track",
            title = "Song Alpha",
            artist = "Artist Alpha",
            album = "Album Alpha",
            albumArtist = "Artist Alpha",
            year = 2019,
            hasStoredTagData = true,
        )
        val root = AppNavState(tab = AppTab.Library(LibraryPage.TRACKS))
        val nav = mutableStateOf(root)
        lateinit var playerModel: PlayerViewModel
        lateinit var libraryModel: LibraryViewModel
        lateinit var dispatcher: OnBackPressedDispatcher
        val modelsReady = AtomicBoolean(false)

        rule.setContent {
            EnoToneTheme {
                NavigationHarness(
                    track = track,
                    nav = nav,
                    onModelsReady = { player, library ->
                        playerModel = player
                        libraryModel = library
                        modelsReady.set(true)
                    },
                    onDispatcherReady = { dispatcher = it },
                )
            }
        }
        rule.waitUntil(10_000) {
            modelsReady.get() &&
                !libraryModel.state.value.loading &&
                libraryModel.state.value.derivedRoute == (LibraryPage.TRACKS to null) &&
                libraryModel.state.value.groupIndex.albumByTrackId.containsKey(track.id)
        }
        val albumKey = requireNotNull(libraryModel.state.value.groupIndex.albumByTrackId[track.id]).key
        rule.runOnIdle { playerModel.onNowPlaying(true) }
        rule.waitUntil(10_000) { playerModel.state.value.nowPlaying }

        rule.onNodeWithContentDescription(
            context.getString(R.string.song_actions_description, track.title),
        ).performClick()
        rule.onNodeWithText(context.getString(R.string.song_action_go_album)).performClick()

        rule.waitUntil(10_000) {
            nav.value.details.lastOrNull() == LibraryDetail.Album(albumKey)
        }
        rule.runOnIdle {
            assertFalse("Переход к альбому должен закрыть плеер", playerModel.state.value.nowPlaying)
        }
        rule.waitUntil(10_000) {
            !playerModel.state.value.nowPlaying &&
                libraryModel.state.value.derivedRoute == (LibraryPage.ALBUMS to albumKey) &&
                libraryModel.state.value.selectedGroup != null
        }
        rule.onNodeWithText("Album Alpha").assertExists()

        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.waitUntil(10_000) {
            nav.value == root && libraryModel.state.value.derivedRoute == (LibraryPage.TRACKS to null)
        }
        rule.onNodeWithText("Song Alpha").assertExists()
        rule.runOnIdle {
            assertEquals(root.tab, nav.value.tab)
            assertFalse(playerModel.state.value.nowPlaying)
        }
    }

    @Composable
    private fun NavigationHarness(
        track: OfflineTrackRow,
        nav: androidx.compose.runtime.MutableState<AppNavState>,
        onModelsReady: (PlayerViewModel, LibraryViewModel) -> Unit,
        onDispatcherReady: (OnBackPressedDispatcher) -> Unit,
    ) {
        val connection = remember(track.id) { FixturePlaybackConnection(track) }
        val repository = remember(track.id) { fixtureRepository(track) }
        val library = remember(track.id) {
            LibraryViewModel(
                accountEmail = "fixture@example.invalid",
                password = "fixture-password",
                initialPage = LibraryPage.TRACKS,
                repository = repository,
                connection = connection,
                artwork = NoArtwork,
                io = Dispatchers.Unconfined,
                unknownAlbumName = "Unknown album",
                messages = AlbumRefreshMessages(),
                managedStatus = MutableStateFlow(ManagedScanStatus()),
                accountEvents = MutableStateFlow(AccountState()),
                dataRevision = MutableStateFlow(0L),
            )
        }
        val player = remember(track.id) {
            PlayerViewModel(
                connection = connection,
                repository = repository,
                credentialsProvider = CredentialsProvider { null },
                artworkLoader = NoArtwork,
                io = Dispatchers.Unconfined,
                useCache = { true },
                dataRevision = MutableStateFlow(0L),
            )
        }
        SideEffect { onModelsReady(player, library) }
        val playerState by player.state.collectAsStateWithLifecycle()
        val libraryState by library.state.collectAsStateWithLifecycle()
        val expanded = playerState.nowPlaying && playerState.playback.queueTotal > 0
        val backController = remember { AppBackController() }
        val target = appBackTarget(expanded, nav.value, searchOpen = false)
        val backOwner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
        SideEffect { onDispatcherReady(backOwner.onBackPressedDispatcher) }
        AppBackGesture(
            enabled = target != AppBackTarget.EXIT,
            onPreview = { backController.preview(target, nav.value, it) },
            onCommit = {
                backController.commit(
                    target = target,
                    nav = nav.value,
                    closePlayer = { player.onNowPlaying(false) },
                    updateNav = { nav.value = it },
                    closeSearch = {},
                )
            },
            onCancel = backController::cancel,
        )
        val context = LocalContext.current
        val artwork = remember(context) { ArtworkPreviewRepository(context) }
        val snapshots = remember { AppLibraryRouteSnapshots() }
        AppMotionRoot(
            inSettings = false,
            diagnostics = false,
            playerExpanded = expanded,
            playerBackProgress = backController.playerProgress(),
            onPlayerExitComplete = backController::finishTransition,
            music = {
                AppLibraryRouteTransition(
                    nav = nav.value,
                    liveState = libraryState,
                    snapshots = snapshots,
                    searchRegistry = null,
                ) { route, state, active ->
                    if (active) {
                        LaunchedEffect(library, route) {
                            route.libraryTarget()?.let { (page, key) ->
                                library.onRouteSelected(page, key)
                            }
                        }
                    }
                    if (route.tab is AppTab.Library) {
                        LibraryScreenContent(
                            state = requireNotNull(state),
                            actions = library,
                            artworkRepository = artwork,
                            accountEmail = "",
                            appPassword = "fixture-password",
                            contentPadding = PaddingValues(),
                            detail = route.details.lastOrNull(),
                            onOpenDetail = { nav.value = AppNavigator.push(nav.value, it) },
                        )
                    } else {
                        Text("Home")
                    }
                }
            },
            settings = { Text("Settings") },
            player = {
                AppNowPlaying(
                    player = playerState,
                    playerModel = player,
                    account = AccountUiState(),
                    onOpenDetail = { nav.value = AppNavigator.push(nav.value, it) },
                    libraryModelOverride = library,
                )
            },
        )
    }

    private fun fixtureRepository(track: OfflineTrackRow) = object : LibraryRepository {
        override suspend fun load(email: String) = LibrarySnapshot(listOf(track), emptyMap())
        override suspend fun refreshAlbum(
            email: String,
            password: String,
            ids: List<String>,
        ): AlbumTagRefreshReport = error("Not used by this navigation test")
        override suspend fun readSavedQueue(): PersistedQueueState? = null
    }

    private class FixturePlaybackConnection(track: OfflineTrackRow) : PlaybackConnection {
        override val state: StateFlow<PlaybackSnapshot> = MutableStateFlow(
            PlaybackSnapshot(
                connected = true,
                playbackState = "PAUSED",
                currentTrackId = track.id,
                title = track.title,
                artist = track.artist,
                durationMs = 120_000L,
                knownDurationMs = 120_000L,
                canSeek = true,
                queuePosition = 1,
                queueTotal = 3,
                hasPrevious = true,
                hasNext = true,
            ),
        )

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun playQueue(
            email: String,
            password: String,
            queue: List<QueueTrack>,
            selectedId: String,
            useCache: Boolean,
            sourceKey: String,
        ) = Unit
        override fun restoreQueue(
            email: String,
            password: String,
            queue: List<QueueTrack>,
            saved: PersistedQueueState,
            useCache: Boolean,
            filtered: Boolean,
        ): Boolean = false
        override fun stopPlayback(): Boolean = true
        override fun playPause() = Unit
        override fun playNext() = Unit
        override fun playPrevious() = Unit
        override fun cycleRepeatMode() = Unit
        override fun toggleShuffle() = Unit
        override fun seekToFraction(fraction: Float) = Unit
    }

    private object NoArtwork : LibraryArtworkLoader {
        override fun peek(email: String, group: AlbumArtworkGroup, size: Int): Bitmap? = null
        override suspend fun cached(email: String, group: AlbumArtworkGroup, size: Int): Bitmap? = null
        override suspend fun fetch(
            email: String,
            password: String,
            group: AlbumArtworkGroup,
            size: Int,
        ): Bitmap? = null
        override suspend fun invalidate(email: String, targets: Collection<PlayingArtworkTarget>) = Unit
    }
}
