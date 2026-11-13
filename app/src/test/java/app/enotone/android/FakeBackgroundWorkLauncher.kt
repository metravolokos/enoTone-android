package app.enotone.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

internal class FakeBackgroundWorkLauncher : BackgroundWorkLauncher {
    override val indexing = MutableStateFlow(ManagedScanStatus())
    override val artwork = MutableStateFlow(ArtworkScanStatus())
    var indexingStarts = 0
    var artworkStarts = 0
    var failStart = false
    val indexingActions = mutableListOf<WorkAction>()
    val artworkActions = mutableListOf<WorkAction>()
    override fun startIndexing(login: StoredWebDavLogin): Boolean {
        if (failStart) error("private exception")
        if (indexing.value.active) return false
        indexingStarts++
        indexing.value = ManagedScanStatus(ManagedScanState.RUNNING)
        return true
    }
    override fun startArtwork(login: StoredWebDavLogin): Boolean {
        if (artwork.value.active) return false
        artworkStarts++
        artwork.value = ArtworkScanStatus(ArtworkScanState.RUNNING)
        return true
    }
    override fun indexingAction(action: WorkAction) { indexingActions += action }
    override fun artworkAction(action: WorkAction) { artworkActions += action }
}

internal val testLogin = StoredWebDavLogin("example@invalid.test", "test-secret")
internal val testSettingsText = SettingsText { id, args -> "$id:${args.joinToString()}" }

@OptIn(ExperimentalCoroutinesApi::class)
internal fun settingsTest(block: suspend TestScope.() -> Unit) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        block()
    } finally {
        Dispatchers.resetMain()
    }
}
