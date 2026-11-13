package app.enotone.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal interface ArtworkScanStatusPort {
    val status: StateFlow<ArtworkScanStatus>
    fun update(
        state: ArtworkScanState,
        completed: Int,
        total: Int,
        terminating: Boolean = false,
    )
}

internal enum class ArtworkScanState { IDLE, RUNNING, PAUSED, STOPPED, COMPLETE, ERROR }

internal data class ArtworkScanStatus(
    val state: ArtworkScanState = ArtworkScanState.IDLE,
    val completed: Int = 0,
    val total: Int = 0,
    val terminating: Boolean = false,
)

/**
 * Credentials are handed directly in RAM. No password in an Intent,
 * PendingIntent, notification, Room, artwork, preferences, or log.
 */
internal object ArtworkScanRuntime : ArtworkScanStatusPort {
    override val status = MutableStateFlow(ArtworkScanStatus())
    private var pending: StoredWebDavLogin? = null

    @Synchronized fun prepare(email: String, password: String): Boolean {
        val credentialsProvided = email.isNotBlank() && password.isNotBlank()
        val available = status.value.state != ArtworkScanState.RUNNING &&
            !status.value.terminating
        if (!credentialsProvided || !available) return false
        pending = StoredWebDavLogin(email, password)
        status.value = ArtworkScanStatus(state = ArtworkScanState.RUNNING)
        return true
    }

    @Synchronized fun claim(): StoredWebDavLogin? =
        pending.also { pending = null }

    @Synchronized override fun update(state: ArtworkScanState, completed: Int, total: Int,
        terminating: Boolean) {
        if (state == ArtworkScanState.RUNNING &&
            (status.value.state != ArtworkScanState.RUNNING || status.value.terminating)) return
        if (state != ArtworkScanState.RUNNING) pending = null
        status.value = ArtworkScanStatus(state, completed, total, terminating)
    }
}
