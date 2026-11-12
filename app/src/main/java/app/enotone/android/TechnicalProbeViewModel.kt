package app.enotone.android

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.enotone.network.FlatLibraryWebDavClient
import app.enotone.network.FlatWebDavHttpError
import app.enotone.network.WebDavDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The UI only receives sanitized report text or numeric listing counts. */
internal data class RemoteListingCounts(
    val playable: Int,
    val directories: Int,
    val nested: Int,
    val nonAudio: Int,
    val invalid: Int,
)
internal interface TechnicalProbeGateway {
    suspend fun webDav(email: String, password: String, file: String, infinity: Boolean): String
    suspend fun remoteCount(email: String, password: String): RemoteListingCounts
}

internal class AndroidTechnicalProbeGateway(
    private val container: AppContainer,
) : TechnicalProbeGateway {
    override suspend fun webDav(
        email: String, password: String, file: String, infinity: Boolean,
    ): String = withContext(container.io) {
        WebDavDiagnostics().inspect(email, password, file, infinity).toSanitizedText()
    }

    override suspend fun remoteCount(email: String, password: String): RemoteListingCounts =
        withContext(container.io) {
            FlatLibraryWebDavClient().use { client ->
                val listing = client.list(email, password)
                RemoteListingCounts(
                    listing.tracks.size, listing.skippedDirectories,
                    listing.skippedNested, listing.skippedNonAudio, listing.invalidEntries,
                )
            }
        }
}

internal sealed interface TechnicalProbeResult {
    data class Report(val text: String) : TechnicalProbeResult
    data class Counts(val value: RemoteListingCounts) : TechnicalProbeResult
    data class HttpFailure(val status: Int) : TechnicalProbeResult
    data object FailedProbe : TechnicalProbeResult
    data object FailedListing : TechnicalProbeResult
}

internal data class TechnicalProbeUiState(
    val busy: Boolean = false,
    val version: Long = 0L,
    val result: TechnicalProbeResult? = null,
)

internal class TechnicalProbeViewModel(
    private val gateway: TechnicalProbeGateway,
) : ViewModel() {
    private val mutable = MutableStateFlow(TechnicalProbeUiState())
    val state: StateFlow<TechnicalProbeUiState> = mutable

    fun check(email: String, password: String, file: String, infinity: Boolean) {
        if (mutable.value.busy || email.isBlank() || password.isBlank()) return
        mutable.update { it.copy(busy = true, result = null) }
        viewModelScope.launch {
            val outcome = try {
                TechnicalProbeResult.Report(gateway.webDav(email, password, file, infinity))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                TechnicalProbeResult.FailedProbe
            }
            publish(outcome)
        }
    }

    fun count(email: String, password: String) {
        if (mutable.value.busy || email.isBlank() || password.isBlank()) return
        mutable.update { it.copy(busy = true, result = null) }
        viewModelScope.launch {
            val outcome = try {
                TechnicalProbeResult.Counts(gateway.remoteCount(email, password))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: FlatWebDavHttpError) {
                TechnicalProbeResult.HttpFailure(error.statusCode)
            } catch (_: Exception) {
                TechnicalProbeResult.FailedListing
            }
            publish(outcome)
        }
    }

    private fun publish(outcome: TechnicalProbeResult) {
        mutable.update { it.copy(result = outcome, busy = false,
            version = it.version + 1) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == TechnicalProbeViewModel::class.java)
                    return TechnicalProbeViewModel(AndroidTechnicalProbeGateway(container)) as T
                }
            }
    }
}
