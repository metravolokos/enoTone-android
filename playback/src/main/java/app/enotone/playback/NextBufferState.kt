package app.enotone.playback

/**
 * Readiness of the item that will play after the current one.
 * This is playback state; the feature layer chooses how each value looks.
 */
enum class NextBufferState {
    NONE,
    LOADING,
    READY,
}
