package app.enotone.android

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import app.enotone.playback.NextBufferState
import app.enotone.android.core.ui.R as CoreUiR

/** App-owned strings plus shared UI Icons v1 resource IDs. */
private val nowPlayingIds = NowPlayingIds(
    playbackError = R.string.playback_error,
    playbackMissingPassword = R.string.playback_missing_password,
    playbackMissingTrack = R.string.playback_missing_track,
    playbackRepeatOne = R.string.playback_repeat_one,
    playbackRepeatAll = R.string.playback_repeat_all,
    playbackRepeatOff = R.string.playback_repeat_off,
    libraryPlayerClose = R.string.library_player_close,
    artworkDescription = R.string.artwork_description,
    icNavAlbums = R.drawable.ic_nav_albums,
    nowPlayingNoArtwork = R.string.now_playing_no_artwork,
    nowPlayingTitle = R.string.now_playing_title,
    playbackDurationUnknown = R.string.playback_duration_unknown,
    icActionPrevious = CoreUiR.drawable.ic_eno_previous,
    playbackPrevious = R.string.playback_previous,
    icActionPause = CoreUiR.drawable.ic_eno_pause,
    icActionPlay = CoreUiR.drawable.ic_eno_play,
    playbackPause = R.string.playback_pause,
    playbackResume = R.string.playback_resume,
    icActionNext = CoreUiR.drawable.ic_eno_next,
    playbackNext = R.string.playback_next,
    playbackShuffleOn = R.string.playback_shuffle_on,
    playbackShuffleOff = R.string.playback_shuffle_off,
    nowPlayingQueueShort = R.string.now_playing_queue_short,
    playbackRepeatControl = R.string.playback_repeat_control,
    nowPlayingSeekKnown = R.string.now_playing_seek_known,
    nowPlayingSeekUnknown = R.string.now_playing_seek_unknown,
    nowPlayingNextLoading = R.string.now_playing_next_loading,
    nowPlayingNextReady = R.string.now_playing_next_ready,
    icActionMore = CoreUiR.drawable.ic_eno_more,
    trackActionsDescription = R.string.song_actions_description,
    nowPlayingQueueDescription = R.string.now_playing_queue_description,
)

/** Keeps existing callers source-compatible through defaults for the new UI state. */
@Composable
internal fun NowPlayingScreen(
    trackId: String,
    title: String,
    artist: String?,
    album: String?,
    albumYear: Int? = null,
    artwork: Bitmap?,
    playbackState: String,
    errorCode: Int,
    positionMs: Long,
    durationMs: Long,
    canSeek: Boolean,
    controlsEnabled: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    queuePosition: Int,
    queueTotal: Int,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    bufferedFraction: Float = 0f,
    hasKnownBufferDuration: Boolean = false,
    nextBufferState: NextBufferState = NextBufferState.NONE,
    onClose: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onMore: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
) {
    FeatureNowPlayingScreen(
        trackId = trackId,
        title = title,
        artist = artist,
        album = album,
        albumYear = albumYear,
        artwork = artwork,
        playbackState = playbackState,
        errorCode = errorCode,
        positionMs = positionMs,
        durationMs = durationMs,
        canSeek = canSeek,
        controlsEnabled = controlsEnabled,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
        queuePosition = queuePosition,
        queueTotal = queueTotal,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleEnabled,
        bufferedFraction = bufferedFraction,
        hasKnownBufferDuration = hasKnownBufferDuration,
        nextTrackState = when (nextBufferState) {
            NextBufferState.NONE -> NowPlayingNextTrackState.NONE
            NextBufferState.LOADING -> NowPlayingNextTrackState.LOADING
            NextBufferState.READY -> NowPlayingNextTrackState.READY
        },
        onClose = onClose,
        onSeekFraction = onSeekFraction,
        onPlayPause = onPlayPause,
        onPrevious = onPrevious,
        onNext = onNext,
        onCycleRepeat = onCycleRepeat,
        onToggleShuffle = onToggleShuffle,
        ids = nowPlayingIds,
        onMore = onMore,
        onOpenQueue = onOpenQueue,
    )
}
