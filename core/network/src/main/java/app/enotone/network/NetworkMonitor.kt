package app.enotone.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide signal for a default network whose internet access Android has validated. */
interface NetworkMonitor {
    val hasValidatedInternet: StateFlow<Boolean>

    /** Releases the callback when the owning application process is explicitly torn down. */
    fun close()
}

/**
 * Observes the system default network without contacting the user's WebDAV server.
 * The application context and callback are retained for the process lifetime, not an Activity.
 */
// The consuming app declares ACCESS_NETWORK_STATE; standalone library lint cannot see that manifest.
@SuppressLint("MissingPermission")
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivityManager = checkNotNull(
        context.applicationContext.getSystemService(ConnectivityManager::class.java),
    )
    private val stateLock = Any()
    private var defaultNetwork: Network? = connectivityManager.activeNetwork
    private val mutableHasValidatedInternet = MutableStateFlow(
        isValidatedInternet(connectivityManager.getNetworkCapabilities(defaultNetwork)),
    )
    private var closed = false

    override val hasValidatedInternet: StateFlow<Boolean> = mutableHasValidatedInternet.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(stateLock) {
                if (closed) return
                defaultNetwork = network
                mutableHasValidatedInternet.value = isValidatedInternet(
                    connectivityManager.getNetworkCapabilities(network),
                )
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            synchronized(stateLock) {
                if (closed || network != defaultNetwork) return
                mutableHasValidatedInternet.value = isValidatedInternet(capabilities)
            }
        }

        override fun onLost(network: Network) {
            synchronized(stateLock) {
                if (closed || network != defaultNetwork) return
                defaultNetwork = null
                mutableHasValidatedInternet.value = false
            }
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    override fun close() {
        val shouldUnregister = synchronized(stateLock) {
            if (closed) {
                false
            } else {
                closed = true
                true
            }
        }
        if (shouldUnregister) connectivityManager.unregisterNetworkCallback(callback)
    }

    private fun isValidatedInternet(capabilities: NetworkCapabilities?): Boolean =
        capabilities?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
}
