package app.enotone.playback

import app.enotone.android.SourceEndpoint

/**
 * Per-item immutable HTTP metadata. Never use the previous file's size or ETag
 * after a Media3 next/previous command. No network or Android dependencies.
 */
internal data class PlaybackRemoteFile(val size: Long, val etag: String?)

internal object WebDavPlaylistRoutes {
    fun index(
        tracks: List<QueueTrack>,
        endpoint: SourceEndpoint = SourceEndpoint.MAILRU_ALL_MUSIC,
    ): Map<String, PlaybackRemoteFile> {
        require(tracks.isNotEmpty() && tracks.size <= PlaybackQueue.MAX_TRACKS) {
            "Invalid playback queue size"
        }
        val byUrl = tracks.associate { track ->
            require(track.remoteSize > 0) { "Invalid playback file size" }
            WebDavPlaybackTarget.url(track.encodedPath, endpoint) to PlaybackRemoteFile(
                track.remoteSize, track.remoteEtag)
        }
        require(byUrl.size == tracks.size) { "Duplicate playback URL" }
        return byUrl
    }
}
