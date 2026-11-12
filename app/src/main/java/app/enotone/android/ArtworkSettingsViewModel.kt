package app.enotone.android

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.enotone.library.LibrarySnapshotPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Gateway can be tested without Android, storage permissions or WebDAV calls. */
internal interface ArtworkSettingsGateway {
    fun mode(): ArtworkLoadMode
    fun setMode(value: ArtworkLoadMode)
    suspend fun bytes(): Long
    suspend fun clear()
    suspend fun resumeAuto(email: String, password: String)
    fun pause()
    fun stop()
}

internal class AndroidArtworkSettingsGateway(
    private val context: Context,
    private val container: AppContainer,
) : ArtworkSettingsGateway {
    private val preferences = ArtworkPreferences(context)

    override fun mode() = preferences.mode()
    override fun setMode(value: ArtworkLoadMode) {
        preferences.setMode(value)
        if (value == ArtworkLoadMode.ON_DEMAND &&
            ArtworkScanRuntime.status.value.state == ArtworkScanState.RUNNING) stop()
    }
    override suspend fun bytes(): Long = container.artworkPreviewRepository.storedBytes()
    override suspend fun clear() = container.artworkPreviewRepository.clearAll()

    override suspend fun resumeAuto(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) return
        // All indexed-tags checks stay off the Compose render and main threads.
        val mayStart = withContext(container.io) {
            val pending = container.database.remoteTrackDao().pendingFastScans(
                LibrarySnapshotPlanner.accountKey(email)).isNotEmpty()
            !pending && ArtworkIndexPlanner.needsPreparation(context, email)
        }
        if (mayStart && ManagedScanRuntime.status.value.state != ManagedScanState.RUNNING &&
            !ManagedScanRuntime.status.value.terminating &&
            ArtworkScanRuntime.prepare(email, password)) {
            try {
                context.startForegroundService(
                    Intent(context, ArtworkIndexingForegroundService::class.java)
                        .setAction(ArtworkIndexingForegroundService.ACTION_START),
                )
            } catch (_: RuntimeException) {
                ArtworkScanRuntime.update(ArtworkScanState.ERROR, 0, 0)
            }
        }
    }

    override fun pause() = command(ArtworkIndexingForegroundService.ACTION_PAUSE)
    override fun stop() = command(ArtworkIndexingForegroundService.ACTION_STOP)
    private fun command(action: String) {
        context.startService(Intent(context, ArtworkIndexingForegroundService::class.java)
            .setAction(action))
    }
}

internal enum class ArtworkClearFeedback { NONE, CLEARED, FAILED }
internal data class ArtworkSettingsUiState(
    val mode: ArtworkLoadMode = ArtworkLoadMode.ON_DEMAND,
    val storedBytes: Long? = null,
    val clearing: Boolean = false,
    val feedback: ArtworkClearFeedback = ArtworkClearFeedback.NONE,
)

/** Emits one immutable StateFlow; UI sends actions rather than performing disk I/O. */
internal class ArtworkSettingsViewModel(
    private val gateway: ArtworkSettingsGateway,
) : ViewModel() {
    private val mutable = MutableStateFlow(ArtworkSettingsUiState(mode = gateway.mode()))
    val state: StateFlow<ArtworkSettingsUiState> = mutable

    fun changeMode(next: ArtworkLoadMode) {
        if (next == mutable.value.mode) return
        try {
            gateway.setMode(next)
            mutable.update { it.copy(mode = next, feedback = ArtworkClearFeedback.NONE) }
        } catch (_: Exception) {
            mutable.update { it.copy(feedback = ArtworkClearFeedback.FAILED) }
        }
    }

    fun refreshSize() {
        viewModelScope.launch {
            try {
                val value = gateway.bytes()
                mutable.update { it.copy(storedBytes = value) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(storedBytes = null) }
            }
        }
    }

    fun clear() {
        if (mutable.value.clearing || ArtworkScanRuntime.status.value.state ==
            ArtworkScanState.RUNNING || ArtworkScanRuntime.status.value.terminating) return
        mutable.update { it.copy(clearing = true, feedback = ArtworkClearFeedback.NONE) }
        viewModelScope.launch {
            try {
                gateway.clear()
                mutable.update { it.copy(storedBytes = 0L, feedback = ArtworkClearFeedback.CLEARED) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(feedback = ArtworkClearFeedback.FAILED) }
            } finally {
                mutable.update { it.copy(clearing = false) }
            }
        }
    }

    fun maybeResume(email: String, password: String, tagsBusy: Boolean) {
        if (mutable.value.mode != ArtworkLoadMode.AUTO_AFTER_IMPORT ||
            tagsBusy || email.isBlank() || password.isBlank()) return
        viewModelScope.launch {
            try {
                gateway.resumeAuto(email, password)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Damaged optional artwork/index data must not break listening.
            }
        }
    }

    fun pause() = gateway.pause()
    fun stop() = gateway.stop()

    companion object {
        fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == ArtworkSettingsViewModel::class.java)
                    return ArtworkSettingsViewModel(
                        AndroidArtworkSettingsGateway(context, container),
                    ) as T
                }
            }
    }
}
