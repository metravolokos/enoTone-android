package app.enotone.android

import app.enotone.network.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FakeNetworkMonitor(initiallyAvailable: Boolean = false) : NetworkMonitor {
    private val mutableHasValidatedInternet = MutableStateFlow(initiallyAvailable)
    override val hasValidatedInternet: StateFlow<Boolean> = mutableHasValidatedInternet.asStateFlow()

    fun setAvailable(available: Boolean) {
        mutableHasValidatedInternet.value = available
    }

    override fun close() = Unit
}
