package app.enotone.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.enotone.android.core.ui.component.EnoBottomBar
import app.enotone.android.core.ui.component.EnoBottomBarItem
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.android.core.ui.component.LocalEnoDockBlur
import app.enotone.android.core.ui.component.rememberEnoBlurState
import app.enotone.library.LibraryPage
import app.enotone.library.OfflineTrackRow
import kotlinx.coroutines.launch

/**
 * Каркас D3 (ADR-0086): содержимое вкладки, над ним закреплённые мини-плеер и плавающая
 * нижняя панель «Домой · Песни · Альбомы · Артисты · Авторы» (скрытые страницы — ADR-0052).
 */
@Composable
internal fun AppMusicShell(
    nav: AppNavState,
    pages: List<LibraryPage>,
    account: AccountUiState,
    player: SharedPlayerUiState,
    playerModel: PlayerViewModel,
    libraryModel: LibraryViewModel?,
    libraryStateHolder: SaveableStateHolder,
    onSelectTab: (AppTab) -> Unit,
    onOpenDetail: (LibraryDetail) -> Unit,
    onSettings: () -> Unit,
    searchRegistry: AppSearchBackRegistry? = null,
) {
    val login = account.libraryLogin
    val library = libraryModel?.state?.collectAsStateWithLifecycle()?.value
    val snapshots = androidx.compose.runtime.remember(libraryModel) { AppLibraryRouteSnapshots() }
    val artworkPreviewRepository = (LocalContext.current.applicationContext as EnoToneApplication)
        .container.artworkPreviewRepository
    val selecting = library?.selecting == true
    val miniVisible = appMiniPlayerVisible(nav, library, player.playback.queueTotal)
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Последняя строка списка поднимается над панелью и мини-плеером (вариант B).
    val contentPadding = PaddingValues(bottom = dockHeight(miniVisible) + navBar)
    // Одно состояние размытия на вкладку: список — источник, полоска/заголовок и (при включённых
    // «Прозрачных панелях», D3.2) мини-плеер и нижняя панель — потребители.
    val blur = rememberEnoBlurState()
    // «Прозрачные панели» (ADR-0088, D3.3): и нижние (мини-плеер, панель), и верхние (заголовок, полоска).
    CompositionLocalProvider(LocalEnoDockBlur provides blur.takeIf { UiEffects.translucentDock }) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AppLibraryRouteTransition(
                nav = nav,
                liveState = library,
                snapshots = snapshots,
                searchRegistry = searchRegistry,
            ) { route, snapshot, active ->
            val recentDetail = route.details.lastOrNull() as? LibraryDetail.RecentlyAdded
            val renderedTab = route.renderedContentTab()
            if (recentDetail != null) {
                libraryStateHolder.SaveableStateProvider(
                    librarySaveKey(AppTab.Library(LibraryPage.TRACKS), route.details),
                ) {
                    RecentlyAddedScreen(
                        kind = recentDetail.kind,
                        state = library,
                        actions = libraryModel ?: PreviewLibraryActions,
                        artworkRepository = artworkPreviewRepository,
                        accountEmail = login?.email.orEmpty(),
                        appPassword = login?.password.orEmpty(),
                        contentPadding = contentPadding,
                        onOpenDetail = onOpenDetail,
                    )
                }
            } else when (val tab = renderedTab) {
                AppTab.Home -> EnoToneHomeScreen(
                    connectedAccount = login != null,
                    songCount = library?.tracks?.size?.takeIf { !library.loading && it > 0 },
                    onSettings = onSettings,
                    onShuffleAll = { libraryModel?.onShuffleLibrary() },
                    contentPadding = contentPadding,
                    shuffleEnabled = library?.playbackConnected == true && !selecting,
                    recentContent = {
                        RecentlyAddedHomeSections(
                            state = library,
                            actions = libraryModel ?: PreviewLibraryActions,
                            artworkRepository = artworkPreviewRepository,
                            accountEmail = login?.email.orEmpty(),
                            appPassword = login?.password.orEmpty(),
                            onOpenDetail = onOpenDetail,
                        )
                    },
                )
                is AppTab.Library -> if (login != null && libraryModel != null) {
                    // Состояние прокрутки переживает «Сейчас играет» (регрессия Huawei после #92).
                    val routeKey = librarySaveKey(tab, route.details)
                    libraryStateHolder.SaveableStateProvider(routeKey) {
                        OfflineLibraryScreen(
                            model = libraryModel,
                            page = tab.page,
                            detail = route.details.lastOrNull(),
                            onOpenDetail = onOpenDetail,
                            visiblePages = pages,
                            accountEmail = login.email,
                            appPassword = login.password,
                            contentPadding = contentPadding,
                            scrollToTop = route.scrollToTop,
                            blur = blur,
                            routeActive = active,
                            routeSnapshot = snapshot,
                            onOpenSettings = onSettings,
                        )
                    }
                }
            }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = DOCK_SIDE, end = DOCK_SIDE, bottom = DOCK_SIDE),
                verticalArrangement = Arrangement.spacedBy(DOCK_GAP),
            ) {
                AppDockMotion(
                    miniVisible = miniVisible,
                    miniPlayer = {
                        EnoToneMiniPlayer(
                            player = player,
                            selecting = selecting,
                            onOpen = { playerModel.onNowPlaying(true) },
                            onPlayPause = playerModel::onPlayPause,
                            onNext = playerModel::onNext,
                        )
                    },
                    bottomBar = {
                        AppBottomBar(
                            nav = nav, pages = pages,
                            enabled = login != null, onSelectTab = onSelectTab,
                        )
                    },
                )
            }
        }
    }
}

internal fun appMiniPlayerVisible(
    nav: AppNavState,
    library: LibraryUiState?,
    queueTotal: Int,
): Boolean {
    val emptyLibraryTab = nav.tab is AppTab.Library && library?.let {
        !it.loading && !it.failed && it.tracks.isEmpty() && it.search.isBlank() &&
            it.managedScanStatus.state == ManagedScanState.COMPLETE &&
            !it.managedScanStatus.terminating
    } == true
    return queueTotal > 0 && !emptyLibraryTab
}

private fun AppNavState.renderedContentTab(): AppTab {
    val isRecentlyAdded = details.lastOrNull() is LibraryDetail.RecentlyAdded
    return if (tab == AppTab.Home && details.isNotEmpty() && !isRecentlyAdded) {
        AppTab.Library(LibraryPage.TRACKS)
    } else tab
}

@Composable
private fun AppBottomBar(
    nav: AppNavState,
    pages: List<LibraryPage>,
    enabled: Boolean,
    onSelectTab: (AppTab) -> Unit,
) {
    val tabs = listOf<AppTab>(AppTab.Home) + pages.map { AppTab.Library(it) }
    val items = tabs.map { tab ->
        when (tab) {
            AppTab.Home -> EnoBottomBarItem(EnoIcons.HOME, stringResource(R.string.nav_home))
            is AppTab.Library -> when (tab.page) {
                LibraryPage.TRACKS -> EnoBottomBarItem(EnoIcons.SONGS, stringResource(R.string.nav_songs))
                LibraryPage.ALBUMS -> EnoBottomBarItem(EnoIcons.ALBUMS, stringResource(R.string.nav_albums))
                LibraryPage.ARTISTS -> EnoBottomBarItem(EnoIcons.ARTISTS, stringResource(R.string.nav_artists))
                LibraryPage.ALBUM_ARTISTS ->
                    EnoBottomBarItem(EnoIcons.ALBUM_ARTISTS, stringResource(R.string.nav_album_artists))
            }
        }
    }
    EnoBottomBar(
        items = items,
        selectedIndex = tabs.indexOf(nav.tab),
        // Без входа «Домой» остаётся доступен, а разделы медиатеки — нет: им нечего показать.
        onSelect = { index -> tabs[index].let { if (enabled || it == AppTab.Home) onSelectTab(it) } },
    )
}

/** Высота закреплённой части снизу: панель 70, мини-плеер 64 + зазор, отступ снизу и воздух. */
private fun dockHeight(miniVisible: Boolean): Dp =
    BAR_HEIGHT + DOCK_SIDE + CONTENT_AIR + if (miniVisible) MINI_HEIGHT + DOCK_GAP else 0.dp

/** Places the single root SnackbarHost just above the same dock column used by AppMusicShell. */
internal fun snackbarBottomOffset(miniVisible: Boolean): Dp =
    DOCK_SIDE + BAR_HEIGHT + DOCK_GAP + if (miniVisible) MINI_HEIGHT + DOCK_GAP else 0.dp

/** Isolate encoding of nested detail keys from the AppMusicShell Compose state machine. */
private fun librarySaveKey(tab: AppTab.Library, details: List<LibraryDetail>): String =
    LIBRARY_STATE_KEY + tab.page.name + ":" + details.joinToString("/") { detail ->
        val kind = when (detail) {
            is LibraryDetail.Album -> "a"
            is LibraryDetail.Artist -> "r"
            is LibraryDetail.AlbumArtist -> "u"
            is LibraryDetail.RecentlyAdded -> "n"
        }
        kind + detail.key.length + ":" + detail.key
    }

private const val LIBRARY_STATE_KEY = "library"
internal val DOCK_SIDE = 12.dp
private val DOCK_GAP = 8.dp
private val BAR_HEIGHT = 70.dp
internal val MINI_HEIGHT = 64.dp
private val CONTENT_AIR = 16.dp

/** «Сейчас играет» — слой поверх любого экрана; сворачивается в мини-плеер (D3). */
@Composable
internal fun AppNowPlaying(
    player: SharedPlayerUiState,
    playerModel: PlayerViewModel,
    account: AccountUiState,
    onOpenDetail: (LibraryDetail) -> Boolean,
    libraryModelOverride: LibraryViewModel? = null,
    /** Shown instead of a Toast when a destination cannot be resolved (D5-C language). */
    onDestinationUnavailable: () -> Unit = {},
) {
    val playback = player.playback
    val login = account.libraryLogin
    val libraryModel = libraryModelOverride ?: login?.let {
        rememberLibraryViewModel(it.email, it.password)
    }
    val library = libraryModel?.state?.collectAsStateWithLifecycle()?.value
    val currentTrack = playback.currentTrackId?.let { library?.groupIndex?.tracksById?.get(it) }
    val trackId = playback.currentTrackId.orEmpty()
    val title = currentTrack?.title ?: playback.title ?: stringResource(R.string.ui_songs_title)
    var actionsVisible by remember(trackId) { mutableStateOf(false) }
    var queueVisible by remember { mutableStateOf(false) }
    var openingDetail by remember(trackId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as EnoToneApplication).container
    }
    QueueSheetVisibilityGuard(playback.queueOrder != null) { queueVisible = false }
    val opener = PlayerDetailOpener(
        resolve = { id, page -> libraryModel?.playerDetail(id, page) },
        currentTrackId = {
            playerModel.state.value.takeIf { it.nowPlaying }?.playback?.currentTrackId
        },
        navigate = onOpenDetail,
        close = { playerModel.onNowPlaying(false) },
        unavailable = onDestinationUnavailable,
    )
    val requestDetail: (LibraryPage) -> Unit = { page ->
        if (!openingDetail) {
            openingDetail = true
            scope.launch {
                try {
                    opener.open(trackId, page)
                } finally {
                    openingDetail = false
                }
            }
        }
    }
    NowPlayingScreen(
        trackId = trackId,
        title = title,
        artist = currentTrack?.artist ?: playback.artist,
        album = currentTrack?.album ?: player.album,
        albumYear = currentTrack?.year,
        artwork = player.largeArtwork ?: player.artwork,
        playbackState = playback.playbackState,
        positionMs = playback.positionMs,
        durationMs = playback.durationMs,
        bufferedFraction = playback.knownDurationMs
            ?.takeIf { it > 0L }
            ?.let { (playback.bufferedPositionMs.toFloat() / it).coerceIn(0f, 1f) }
            ?: 0f,
        hasKnownBufferDuration = playback.knownDurationMs?.let { it > 0L } == true,
        nextBufferState = playback.nextBufferState,
        canSeek = playback.canSeek,
        controlsEnabled = playback.connected,
        hasPrevious = playback.hasPrevious,
        hasNext = playback.hasNext,
        queuePosition = playback.queuePosition,
        queueTotal = playback.queueTotal,
        repeatMode = playback.repeatMode,
        shuffleEnabled = playback.shuffleEnabled,
        onClose = { playerModel.onNowPlaying(false) },
        onSeekFraction = playerModel::onSeek,
        onPlayPause = playerModel::onPlayPause,
        onPrevious = playerModel::onPrevious,
        onNext = playerModel::onNext,
        onCycleRepeat = playerModel::onRepeat,
        onToggleShuffle = playerModel::onShuffle,
        onMore = { actionsVisible = true },
        onOpenQueue = { queueVisible = true },
    )
    QueueSheet(
        visible = queueVisible,
        queue = playback.queueOrder,
        shuffleEnabled = playback.shuffleEnabled,
        isPlaying = playback.playbackState == "PLAYING",
        tracksById = library?.groupIndex?.tracksById.orEmpty(),
        artwork = { track ->
            TrackArtworkThumbnail(
                trackId = track.id,
                artworkGroup = library?.artworkCatalog?.byTrackId?.get(track.id),
                repository = appContainer.artworkPreviewRepository,
                accountEmail = login?.email.orEmpty(),
                appPassword = login?.password.orEmpty(),
                allowNetwork = login != null,
            )
        },
        onDismiss = { queueVisible = false },
        onSelectPosition = playerModel::onQueuePositionSelected,
    )
    NowPlayingActionSheet(
        visible = actionsVisible,
        trackId = trackId,
        currentTrack = currentTrack,
        title = title,
        artist = playback.artist,
        album = player.album,
        library = library,
        actions = libraryModel ?: PreviewLibraryActions,
        login = login,
        onDismiss = { actionsVisible = false },
        onRequestDetail = requestDetail,
    )
}

@Composable
private fun NowPlayingActionSheet(
    visible: Boolean,
    trackId: String,
    currentTrack: OfflineTrackRow?,
    title: String,
    artist: String?,
    album: String?,
    library: LibraryUiState?,
    actions: LibraryScreenActions,
    login: StoredWebDavLogin?,
    onDismiss: () -> Unit,
    onRequestDetail: (LibraryPage) -> Unit,
) {
    if (visible) {
        val track = currentTrack ?: OfflineTrackRow(
            id = trackId,
            title = title,
            artist = artist,
            album = album,
        )
        val appContext = LocalContext.current.applicationContext
        val container = (appContext as EnoToneApplication).container
        val emptyArtworkCatalog = remember { LibraryUiState().artworkCatalog }
        TrackActionsHost(
            track = track,
            groupIndex = library?.groupIndex ?: LibraryGroupIndex(),
            actions = actions,
            currentDetail = null,
            artworkCatalog = library?.artworkCatalog ?: emptyArtworkCatalog,
            artworkRepository = container.artworkPreviewRepository,
            accountEmail = login?.email.orEmpty(),
            appPassword = login?.password.orEmpty(),
            onDismiss = onDismiss,
            onOpenDetail = {},
            onRequestDetail = onRequestDetail,
        )
    }
}
