package app.enotone.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import app.enotone.network.AndroidNetworkMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworkMonitorTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun signalNeedsBothInternetAndValidatedCapabilities() {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val shadow = Shadows.shadowOf(connectivityManager)
        val network = checkNotNull(connectivityManager.activeNetwork)
        val existingCallbacks = shadow.networkCallbacks.toSet()
        val monitor = AndroidNetworkMonitor(context)

        try {
            val callback = (shadow.networkCallbacks - existingCallbacks).single()
            callback.onAvailable(network)
            callback.onCapabilitiesChanged(network, capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            assertFalse(monitor.hasValidatedInternet.value)

            callback.onCapabilitiesChanged(network, capabilities(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            assertFalse(monitor.hasValidatedInternet.value)

            callback.onCapabilitiesChanged(
                network,
                capabilities(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET,
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                ),
            )
            assertTrue(monitor.hasValidatedInternet.value)

            callback.onCapabilitiesChanged(network, capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            assertFalse(monitor.hasValidatedInternet.value)
        } finally {
            monitor.close()
        }
        assertEquals(existingCallbacks, shadow.networkCallbacks)
    }

    @Test
    fun staleNetworkEventsDoNotReplaceCurrentDefaultNetwork() {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val shadow = Shadows.shadowOf(connectivityManager)
        val existingCallbacks = shadow.networkCallbacks.toSet()
        val monitor = AndroidNetworkMonitor(context)
        val callback = (shadow.networkCallbacks - existingCallbacks).single()
        val networkA = ShadowNetwork.newInstance(101)
        val networkB = ShadowNetwork.newInstance(202)

        try {
            callback.onAvailable(networkA)
            callback.onCapabilitiesChanged(networkA, capabilities(
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            ))
            assertTrue(monitor.hasValidatedInternet.value)

            callback.onAvailable(networkB)
            callback.onCapabilitiesChanged(networkB, capabilities(
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            ))
            assertTrue(monitor.hasValidatedInternet.value)

            callback.onCapabilitiesChanged(networkA, capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            callback.onLost(networkA)
            assertTrue(monitor.hasValidatedInternet.value)
        } finally {
            monitor.close()
        }
        assertEquals(existingCallbacks, shadow.networkCallbacks)
    }

    @Test
    fun losingCurrentDefaultNetworkClearsAvailability() {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val shadow = Shadows.shadowOf(connectivityManager)
        val existingCallbacks = shadow.networkCallbacks.toSet()
        val monitor = AndroidNetworkMonitor(context)
        val callback = (shadow.networkCallbacks - existingCallbacks).single()
        val network = ShadowNetwork.newInstance(303)

        try {
            callback.onAvailable(network)
            callback.onCapabilitiesChanged(network, capabilities(
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            ))
            assertTrue(monitor.hasValidatedInternet.value)

            callback.onLost(network)
            assertFalse(monitor.hasValidatedInternet.value)
        } finally {
            monitor.close()
        }
        assertEquals(existingCallbacks, shadow.networkCallbacks)
    }

    @Test
    fun callbacksAfterCloseDoNotChangeAvailability() {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val shadow = Shadows.shadowOf(connectivityManager)
        val existingCallbacks = shadow.networkCallbacks.toSet()
        val monitor = AndroidNetworkMonitor(context)
        val callback = (shadow.networkCallbacks - existingCallbacks).single()
        val networkA = ShadowNetwork.newInstance(404)
        val networkB = ShadowNetwork.newInstance(505)

        try {
            callback.onAvailable(networkA)
            callback.onCapabilitiesChanged(networkA, capabilities(
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            ))
            assertTrue(monitor.hasValidatedInternet.value)

            monitor.close()
            callback.onLost(networkA)
            callback.onAvailable(networkB)
            callback.onCapabilitiesChanged(networkB, capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET))

            assertTrue(monitor.hasValidatedInternet.value)
        } finally {
            monitor.close()
        }
        assertEquals(existingCallbacks, shadow.networkCallbacks)
    }

    @Test
    fun processContainerKeepsOneCallbackAcrossActivityRecreation() {
        val application = ApplicationProvider.getApplicationContext<EnoToneApplication>()
        val connectivityManager = application.getSystemService(ConnectivityManager::class.java)
        val shadow = Shadows.shadowOf(connectivityManager)
        val container = application.container
        val monitor = container.networkMonitor
        val callbacks = shadow.networkCallbacks.toSet()
        assertEquals(1, callbacks.size)

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            activity.recreate()
            assertSame(container, application.container)
            assertSame(monitor, application.container.networkMonitor)
            assertEquals(callbacks, shadow.networkCallbacks)
        } finally {
            activity.pause().stop().destroy()
            monitor.close()
        }
    }

    @Test
    fun fakePublishesAvailabilityChangesToItsStateFlow() {
        val fake = FakeNetworkMonitor()
        val container = AppContainer(context, networkMonitor = fake)

        assertFalse(container.networkMonitor.hasValidatedInternet.value)
        fake.setAvailable(true)
        assertTrue(container.networkMonitor.hasValidatedInternet.value)
        fake.setAvailable(false)
        assertFalse(container.networkMonitor.hasValidatedInternet.value)
    }

    private fun capabilities(vararg values: Int): NetworkCapabilities {
        val capabilities = NetworkCapabilities()
        val shadow = Shadows.shadowOf(capabilities)
        values.forEach { shadow.addCapability(it) }
        return capabilities
    }
}
