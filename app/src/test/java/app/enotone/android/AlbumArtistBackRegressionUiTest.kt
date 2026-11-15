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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
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
import app.enotone.playback.QueueEditStatus
import app.enotone.playback.QueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Реальный VM + route-container + LibraryScreenContent; никакой фиктивной onGroupSelected. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w393dp-h852dp")
class AlbumArtistBackRegressionUiTest {
    @get:Rule val rule = createComposeRule()
    private val page = LibraryPage.ALBUM_ARTISTS
    private val root = AppNavState(tab = AppTab.Library(page))

    @Test fun albumArtistOpensGroupAndSystemBackReturnsToList() {
        val snapshots = AppLibraryRouteSnapshots()
        var nav by mutableStateOf(root)
        lateinit var dispatcher: OnBackPressedDispatcher
        lateinit var model: LibraryViewModel
        rule.setContent {
            model = remember { testModel() }
            EnoToneTheme {
                AlbumArtistHost(model, snapshots, nav,
                    onNav = { nav = it }, onDispatcher = { dispatcher = it })
            }
        }
        rule.waitForIdle()
        rule.waitUntil(10000) {
            val state = model.state.value
            !state.loading && state.derivedRoute == (page to null) && state.shownGroups.isNotEmpty()
        }
        val key = model.state.value.groupIndex.albumArtists.single().key
        rule.onNodeWithText("Author Alpha").performClick()
        rule.runOnIdle {
            assertEquals("Выбор автора должен быть в навигационном стеке",
                LibraryDetail.AlbumArtist(key), nav.details.lastOrNull())
        }
        rule.waitUntil(10000) { model.state.value.selectedGroupKey == key }
        rule.waitUntil(10000) { model.state.value.derivedRoute == (page to key) }
        assertNotNull(model.state.value.selectedGroup)
        rule.onNodeWithText("Song Alpha").assertExists()
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.runOnIdle { assertEquals("Back должен сделать pop, а не Home", root, nav) }
        rule.waitUntil(10000) { model.state.value.derivedRoute == (page to null) }
        rule.onNodeWithText("Author Alpha").assertExists()
        rule.runOnIdle { assertEquals(root.tab, nav.tab) }
    }

    @Composable
    private fun AlbumArtistHost(
        model: LibraryViewModel,
        snapshots: AppLibraryRouteSnapshots,
        nav: AppNavState,
        onNav: (AppNavState) -> Unit,
        onDispatcher: (OnBackPressedDispatcher) -> Unit,
    ) {
        val state by model.state.collectAsStateWithLifecycle()
        val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
        SideEffect { onDispatcher(owner.onBackPressedDispatcher) }
        val target = appBackTarget(false, nav, false)
        AppBackGesture(
            enabled = target != AppBackTarget.EXIT,
            onPreview = {},
            onCommit = { AppNavigator.back(nav)?.let(onNav) },
        )
        val context = LocalContext.current
        val artwork = remember { ArtworkPreviewRepository(context) }
        AppLibraryRouteTransition(
            nav = nav, liveState = state, snapshots = snapshots,
            playerExpanded = false, backProgress = 0f,
            backPreviewRoute = null, searchRegistry = null,
        ) { route, routeState, active ->
            if (active) {
                LaunchedEffect(model, route) {
                    route.libraryTarget()?.let { (page, key) -> model.onRouteSelected(page, key) }
                }
            }
            if (route.tab == AppTab.Home) {
                Text("Home")
            } else {
                LibraryScreenContent(
                    state = requireNotNull(routeState), actions = model,
                    artworkRepository = artwork, accountEmail = "", appPassword = "",
                    contentPadding = PaddingValues(),
                    detail = route.details.lastOrNull(),
                    onOpenDetail = { onNav(AppNavigator.push(nav, it)) },
                )
            }
        }
    }

    private fun testModel(): LibraryViewModel {
        val row = OfflineTrackRow(
            id = "one", title = "Song Alpha", artist = "Track Performer",
            album = "Album Alpha", albumArtist = "Author Alpha", hasStoredTagData = true,
        )
        val repo = object : LibraryRepository {
            override suspend fun load(email: String) = LibrarySnapshot(listOf(row), emptyMap())
            override suspend fun readSavedQueue(): PersistedQueueState? = null
            override suspend fun refreshAlbum(email: String, password: String, ids: List<String>):
                AlbumTagRefreshReport = error("Not used")
        }
        return LibraryViewModel(
            accountEmail = "example@invalid.test", password = "not-a-secret",
            initialPage = page, repository = repo, connection = NoPlayback,
            artwork = NoArtwork, io = Dispatchers.Unconfined,
            unknownAlbumName = "Unknown", messages = AlbumRefreshMessages(),
            managedStatus = MutableStateFlow(ManagedScanStatus()),
            accountEvents = MutableStateFlow(AccountState()),
            dataRevision = MutableStateFlow(0L),
        )
    }

    private object NoArtwork : LibraryArtworkLoader {
        override fun peek(email: String, group: AlbumArtworkGroup, size: Int): Bitmap? = null
        override suspend fun cached(email: String, group: AlbumArtworkGroup, size: Int): Bitmap? = null
        override suspend fun fetch(
            email: String, password: String, group: AlbumArtworkGroup, size: Int,
        ): Bitmap? = null
        override suspend fun invalidate(
            email: String, targets: Collection<PlayingArtworkTarget>,
        ) = Unit
    }

    private object NoPlayback : PlaybackConnection {
        override val state: StateFlow<PlaybackSnapshot> =
            MutableStateFlow(PlaybackSnapshot(connected = true))
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun playQueue(
            email: String, password: String, queue: List<QueueTrack>,
            selectedId: String, useCache: Boolean, sourceKey: String,
        ) = Unit
        override fun restoreQueue(
            email: String, password: String, queue: List<QueueTrack>,
            saved: PersistedQueueState, useCache: Boolean, filtered: Boolean,
        ): Boolean = true
        override fun stopPlayback(): Boolean = true
        override fun playPause() = Unit
        override fun playNext() = Unit
        override fun playPrevious() = Unit
        override fun insertNext(tracks: List<QueueTrack>) = QueueEditResult(QueueEditStatus.APPLIED, tracks.size)
        override fun append(tracks: List<QueueTrack>) = QueueEditResult(QueueEditStatus.APPLIED, tracks.size)
        override fun cycleRepeatMode() = Unit
        override fun toggleShuffle() = Unit
        override fun seekToFraction(fraction: Float) = Unit
    }
}
