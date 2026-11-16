package app.enotone.android

/** UI-only state; :feature:player stays independent of the Media3 playback module. */
enum class NowPlayingNextTrackState { NONE, LOADING, READY }

/** UI resource IDs come from :app; no feature module may depend on application R. */
/** Ресурсные идентификаторы перечислены явно: дизайн остаётся в :app. */
@Suppress("LongParameterList")
data class NowPlayingIds(
    val playbackError: Int,
    val playbackMissingPassword: Int,
    val playbackMissingTrack: Int,
    val playbackRepeatOne: Int,
    val playbackRepeatAll: Int,
    val playbackRepeatOff: Int,
    val libraryPlayerClose: Int,
    val nowPlayingTitle: Int,
    val artworkDescription: Int,
    val icNavAlbums: Int,
    val nowPlayingNoArtwork: Int,
    val playbackDurationUnknown: Int,
    val icActionPrevious: Int,
    val playbackPrevious: Int,
    val icActionPause: Int,
    val icActionPlay: Int,
    val playbackPause: Int,
    val playbackResume: Int,
    val icActionNext: Int,
    val playbackNext: Int,
    val playbackShuffleOn: Int,
    val playbackShuffleOff: Int,
    val nowPlayingQueueShort: Int,
    val playbackRepeatControl: Int,
    val nowPlayingSeekKnown: Int,
    val nowPlayingSeekUnknown: Int,
    val nowPlayingNextLoading: Int,
    val nowPlayingNextReady: Int,
)
