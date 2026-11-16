package app.enotone.android

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import app.enotone.playback.NextBufferState

/** App-owned string/drawable IDs; neither translations nor icons move before D2. */
private val nowPlayingIds = NowPlayingIds(
    playbackError = R.string.playback_error,
    playbackMissingPassword = R.string.playback_missing_password,
    playbackMissingTrack = R.string.playback_missing_track,
    playbackRepeatOne = R.string.playback_repeat_one,
    playbackRepeatAll = R.string.playback_repeat_all,
    playbackRepeatOff = R.string.playback_repeat_off,
    libraryPlayerClose = R.string.library_player_close,
    nowPlayingTitle = R.string.now_playing_title,
    artworkDescription = R.string.artwork_description,
    icNavAlbums = R.drawable.ic_nav_albums,
    nowPlayingNoArtwork = R.string.now_playing_no_artwork,
    playbackDurationUnknown = R.string.playback_duration_unknown,
    icActionPrevious = R.drawable.ic_action_previous,
    playbackPrevious = R.string.playback_previous,
    icActionPause = R.drawable.ic_action_pause,
    icActionPlay = R.drawable.ic_action_play,
    playbackPause = R.string.playback_pause,
    playbackResume = R.string.playback_resume,
    icActionNext = R.drawable.ic_action_next,
    playbackNext = R.string.playback_next,
    playbackShuffleOn = R.string.playback_shuffle_on,
    playbackShuffleOff = R.string.playback_shuffle_off,
    nowPlayingQueueShort = R.string.now_playing_queue_short,
    playbackRepeatControl = R.string.playback_repeat_control,
    nowPlayingSeekKnown = R.string.now_playing_seek_known,
    nowPlayingSeekUnknown = R.string.now_playing_seek_unknown,
    nowPlayingNextLoading = R.string.now_playing_next_loading,
    nowPlayingNextReady = R.string.now_playing_next_ready,
)

/** Keeps existing callers source-compatible through defaults for the new UI state. */
@Composable
internal fun NowPlayingScreen(
    trackId: String,
    title: String,
    artist: String?,
    album: String?,
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
    predictiveBackProgress: Float = 0f,
    onDragProgress: (Float) -> Unit = {},
) {
    FeatureNowPlayingScreen(
        trackId = trackId,
        title = title,
        artist = artist,
        album = album,
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
        predictiveBackProgress = predictiveBackProgress,
        onDragProgress = onDragProgress,
    )
}
