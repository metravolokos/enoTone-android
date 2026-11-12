package app.enotone.playback

import androidx.media3.common.MediaMetadata

/** Метаданные элемента очереди из сохранённых тегов Room: без путей, ETag и учётных данных. */
object QueueMetadata {
    fun of(track: QueueTrack): MediaMetadata = MediaMetadata.Builder()
        .setTitle(track.title)
        .setArtist(track.artist?.takeIf { it.isNotBlank() })
        .setAlbumTitle(track.album?.takeIf { it.isNotBlank() })
        .build()
}
