package app.enotone.playback

/**
 * Anonymous, in-memory measurement of the last media-item handoff.
 *
 * The measurement starts at a local skip command or a Media3 item-transition
 * callback, not at the end of the previous acoustic sample. READY and
 * isPlaying are Media3 states, not a proof of gapless or audible onset.
 * No media IDs, paths, URLs, tags, credentials or telemetry are retained.
 */
enum class HandoffOrigin { NEXT_BUTTON, PREVIOUS_BUTTON, AUTO, MEDIA_SESSION }
enum class HandoffPhase { BUFFERING, READY, FAILED }

data class HandoffSnapshot(
    val origin: HandoffOrigin,
    val phase: HandoffPhase,
    val preparedNext: Boolean,
    val bufferingMs: Long?,
    val readyMs: Long?,
    val playingMs: Long?,
)

class PlaybackHandoffTracker {
    private var awaitingInitialItem = true
    private var manualRequest: Pair<HandoffOrigin, Long>? = null
    private var activeSinceMs: Long? = null
    private var awaitingPlaying = false
    private var last: HandoffSnapshot? = null

    fun snapshot(): HandoffSnapshot? = last

    fun startNewQueue() {
        awaitingInitialItem = true
        manualRequest = null
        activeSinceMs = null
        awaitingPlaying = false
        last = null
    }

    fun beginButtonSkip(origin: HandoffOrigin, nowMs: Long) {
        require(origin == HandoffOrigin.NEXT_BUTTON ||
            origin == HandoffOrigin.PREVIOUS_BUTTON)
        manualRequest = origin to nowMs
    }

    /** Ignore the initial item of a freshly selected playlist. */
    fun onItemChanged(automatic: Boolean, preparedNext: Boolean, nowMs: Long) {
        if (awaitingInitialItem) {
            awaitingInitialItem = false
            manualRequest = null
            return
        }
        val manual = manualRequest
        manualRequest = null
        val origin = manual?.first ?: if (automatic) HandoffOrigin.AUTO
            else HandoffOrigin.MEDIA_SESSION
        activeSinceMs = manual?.second ?: nowMs
        awaitingPlaying = true
        last = HandoffSnapshot(
            origin = origin,
            phase = HandoffPhase.BUFFERING,
            preparedNext = preparedNext,
            bufferingMs = null,
            readyMs = null,
            playingMs = null,
        )
    }

    fun onBuffering(nowMs: Long) {
        val began = activeSinceMs ?: return
        val current = last ?: return
        if (current.readyMs != null || current.bufferingMs != null) return
        last = current.copy(bufferingMs = (nowMs - began).coerceAtLeast(0L))
    }

    fun onReady(nowMs: Long) {
        val began = activeSinceMs ?: return
        val current = last ?: return
        if (current.readyMs != null) return
        last = current.copy(
            phase = HandoffPhase.READY,
            readyMs = (nowMs - began).coerceAtLeast(0L),
        )
        if (!awaitingPlaying) activeSinceMs = null
    }

    fun onPlaying(nowMs: Long) {
        if (!awaitingPlaying) return
        val began = activeSinceMs ?: return
        val current = last ?: return
        last = current.copy(
            playingMs = (nowMs - began).coerceAtLeast(0L),
        )
        awaitingPlaying = false
        if (current.readyMs != null) activeSinceMs = null
    }

    fun onError() {
        val current = last ?: return
        if (activeSinceMs == null) return
        last = current.copy(phase = HandoffPhase.FAILED)
        activeSinceMs = null
        awaitingPlaying = false
        manualRequest = null
    }

    fun stop() {
        awaitingInitialItem = true
        activeSinceMs = null
        manualRequest = null
        awaitingPlaying = false
        last = null
    }
}
