package app.enotone.android

/**
 * The source/version identity of an embedded picture, never an album-wide
 * assumption: individual tracks in one album may contain different artwork.
 * refreshEpoch only triggers UI recomposition after a confirmed manual refresh;
 * it is deliberately NOT part of the persistent cache key.
 */
data class PlayingArtworkTarget(
    val id: String,
    val encodedPath: String,
    val remoteSize: Long,
    val remoteEtag: String?,
    val refreshEpoch: Int = 0,
)
