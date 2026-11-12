package app.enotone.android

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.enotone.library.ManagedIndexMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Intent carries ACTION only; credentials remain in ManagedScanRuntime RAM. */
internal interface ManagedIndexSettingsGateway {
    fun start(email: String, password: String, continuous: Boolean): Boolean
    fun pause()
    fun stop()
}
internal class AndroidManagedIndexSettingsGateway(
    context: Context,
) : ManagedIndexSettingsGateway {
    private val context = context.applicationContext
    override fun start(email: String, password: String, continuous: Boolean): Boolean {
        val status = ManagedScanRuntime.status.value
        if (email.isBlank() || password.isBlank() ||
            status.state == ManagedScanState.RUNNING || status.terminating) return false
        val mode = if (continuous) ManagedIndexMode.CONTINUOUS else ManagedIndexMode.BOUNDED
        if (!ManagedScanRuntime.prepare(email, password, mode)) return false
        return try {
            context.startForegroundService(
                Intent(context, IndexingForegroundService::class.java)
                    .setAction(IndexingForegroundService.ACTION_START))
            true
        } catch (_: RuntimeException) {
            ManagedScanRuntime.update(ManagedScanState.ERROR)
            false
        }
    }
    override fun pause() = send(IndexingForegroundService.ACTION_PAUSE)
    override fun stop() = send(IndexingForegroundService.ACTION_STOP)
    private fun send(action: String) {
        context.startService(Intent(context, IndexingForegroundService::class.java)
            .setAction(action))
    }
}

internal data class ManagedIndexCommandState(
    val attempts: Long = 0,
    val lastStartSucceeded: Boolean? = null,
)
internal class ManagedIndexSettingsViewModel(
    private val gateway: ManagedIndexSettingsGateway,
) : ViewModel() {
    private val mutable = MutableStateFlow(ManagedIndexCommandState())
    val state: StateFlow<ManagedIndexCommandState> = mutable

    fun start(email: String, password: String, continuous: Boolean) {
        val started = gateway.start(email, password, continuous)
        mutable.update { it.copy(attempts = it.attempts + 1, lastStartSucceeded = started) }
    }
    fun pause() = gateway.pause()
    fun stop() = gateway.stop()

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == ManagedIndexSettingsViewModel::class.java)
                    return ManagedIndexSettingsViewModel(
                        AndroidManagedIndexSettingsGateway(context)) as T
                }
            }
    }
}
