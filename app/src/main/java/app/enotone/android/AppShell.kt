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
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    libraryStateHolder: SaveableStateHolder,
    onSelectTab: (AppTab) -> Unit,
    onSettings: () -> Unit,
) {
    val login = account.libraryLogin
    val libraryModel = login?.let { rememberLibraryViewModel(it.email, it.password) }
    val library = libraryModel?.state?.collectAsStateWithLifecycle()?.value
    val selecting = library?.selecting == true
    val miniVisible = player.playback.queueTotal > 0
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Последняя строка списка поднимается над панелью и мини-плеером (вариант B).
    val contentPadding = PaddingValues(bottom = dockHeight(miniVisible) + navBar)
    // Одно состояние размытия на вкладку: список — источник, полоска/заголовок и (при включённых
    // «Прозрачных панелях», D3.2) мини-плеер и нижняя панель — потребители.
    val blur = rememberEnoBlurState()
    // «Прозрачные панели» (ADR-0088, D3.3): и нижние (мини-плеер, панель), и верхние (заголовок, полоска).
    CompositionLocalProvider(LocalEnoDockBlur provides blur.takeIf { UiEffects.translucentDock }) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val tab = nav.tab) {
                AppTab.Home -> EnoToneHomeScreen(
                    connectedAccount = login != null,
                    songCount = library?.tracks?.size?.takeIf { !library.loading && it > 0 },
                    onSettings = onSettings,
                    onShuffleAll = { libraryModel?.onShuffleLibrary() },
                    contentPadding = contentPadding,
                    shuffleEnabled = library?.playbackConnected == true && !selecting,
                )
                is AppTab.Library -> if (login != null && libraryModel != null) {
                    // Состояние прокрутки переживает «Сейчас играет» (регрессия Huawei после #92).
                    libraryStateHolder.SaveableStateProvider(LIBRARY_STATE_KEY) {
                        OfflineLibraryScreen(
                            model = libraryModel,
                            page = tab.page,
                            visiblePages = pages,
                            accountEmail = login.email,
                            appPassword = login.password,
                            contentPadding = contentPadding,
                            scrollToTop = nav.scrollToTop,
                            blur = blur,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = DOCK_SIDE, end = DOCK_SIDE, bottom = DOCK_SIDE),
                verticalArrangement = Arrangement.spacedBy(DOCK_GAP),
            ) {
                EnoToneMiniPlayer(
                    player = player,
                    selecting = selecting,
                    onOpen = { playerModel.onNowPlaying(true) },
                    onPlayPause = playerModel::onPlayPause,
                    onNext = playerModel::onNext,
                )
                AppBottomBar(nav = nav, pages = pages, enabled = login != null, onSelectTab = onSelectTab)
            }
        }
    }
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

private const val LIBRARY_STATE_KEY = "library"
private val DOCK_SIDE = 12.dp
private val DOCK_GAP = 8.dp
private val BAR_HEIGHT = 70.dp
private val MINI_HEIGHT = 64.dp
private val CONTENT_AIR = 16.dp

/** «Сейчас играет» — слой поверх любого экрана; сворачивается в мини-плеер (D3). */
@Composable
internal fun AppNowPlaying(player: SharedPlayerUiState, playerModel: PlayerViewModel) {
    val playback = player.playback
    NowPlayingScreen(
        trackId = playback.currentTrackId.orEmpty(),
        title = playback.title ?: stringResource(R.string.ui_songs_title),
        artist = playback.artist,
        album = player.album,
        artwork = player.largeArtwork ?: player.artwork,
        playbackState = playback.playbackState,
        errorCode = playback.errorCode,
        positionMs = playback.positionMs,
        durationMs = playback.durationMs,
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
    )
}
