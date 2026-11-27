package app.enotone.playback

/** Logical Next/Previous while Media3 may still expose only a staged seed. */
internal fun PreviewAudioPlayback.seekToNextInPlaybackOrder(): Boolean = when (
    val next = queueNavigation.adjacentPosition(player.currentMediaItem?.mediaId, forward = true)
) {
    null -> false
    PlaybackQueueNavigation.NO_POSITION -> true
    else -> {
        queueNavigation.clearPendingSeek()
        noteNextButton()
        skipToQueuePosition(next)
    }
}

/** True when the logical queue handled the command; false requests native fallback. */
internal fun PreviewAudioPlayback.seekToPreviousInPlaybackOrder(): Boolean = when (
    val previous = queueNavigation.adjacentPosition(player.currentMediaItem?.mediaId, forward = false)
) {
    null -> false
    PlaybackQueueNavigation.NO_POSITION -> true
    else -> {
        queueNavigation.clearPendingSeek()
        notePreviousButton()
        skipToQueuePosition(previous)
    }
}
