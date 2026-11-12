package app.enotone.android

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.res.stringResource
import app.enotone.library.LibraryPage
import app.enotone.library.LibraryPageVisibility

@Composable
internal fun SettingsMusicDestination(
    player: SharedPlayerUiState,
    playerModel: PlayerViewModel,
    account: AccountUiState,
    showingLibrary: Boolean,
    showingSettings: Boolean,
    initialLibraryPage: LibraryPage,
    libraryStateHolder: SaveableStateHolder,
    appContext: Context,
    onLibraryBack: () -> Unit,
    onSettings: () -> Unit,
    onOpenPage: (LibraryPage) -> Unit,
): Boolean {
    // Full player is app-scoped: navigation to Home never clears it.
    return when {
    player.nowPlaying && player.playback.queueTotal > 0 -> {
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
        true
    }
    showingLibrary && account.libraryLogin != null -> {
        val login = requireNotNull(account.libraryLogin)
        libraryStateHolder.SaveableStateProvider(LIBRARY_STATE_KEY) {
            OfflineLibraryScreen(
                accountEmail = login.email, appPassword = login.password,
                initialPage = initialLibraryPage,
                onBack = onLibraryBack,
                player = player,
                onPlayerOpen = { playerModel.onNowPlaying(true) },
                onPlayerPlayPause = playerModel::onPlayPause,
                onPlayerNext = playerModel::onNext,
            )
        }
        true
    }

    // The previous initial diagnostic screen is now a separate Settings route.
    // Read the existing preferences; no Room query or WebDAV request is needed.
    !showingSettings -> {
        val pages = LibraryPageVisibility.visible(
            LibraryPagePreferences(appContext).hiddenPages())
        EnoToneHomeScreen(
            visiblePages = pages,
            connectedAccount = account.libraryLogin != null,
            onSettings = onSettings,
            onOpenPage = onOpenPage,
            miniPlayer = {
                EnoToneMiniPlayer(
                    player = player,
                    onOpen = { playerModel.onNowPlaying(true) },
                    onPlayPause = playerModel::onPlayPause,
                    onNext = playerModel::onNext,
                )
            },
        )
        true
    }

    else -> false
    }
}

private const val LIBRARY_STATE_KEY = "library"
