package app.enotone.android

import android.app.Application
import android.content.Context
import app.enotone.database.EnoToneDatabase
import app.enotone.library.LibraryRepository
import app.enotone.library.RoomLibraryRepository
import app.enotone.network.AndroidNetworkMonitor
import app.enotone.network.NetworkMonitor
import app.enotone.playback.QueueStateFileStore
import app.enotone.playlist.PlaylistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope

/** Application-scope manual wiring (ADR-0077). No ViewModel or secrets are retained here. */
class AppContainer(
    context: Context,
    val io: CoroutineDispatcher = Dispatchers.IO,
    val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    val networkMonitor: NetworkMonitor = AndroidNetworkMonitor(context.applicationContext),
) {
    private val appContext = context.applicationContext
    internal val accountSession = AccountSession()
    val database: EnoToneDatabase by lazy { EnoToneDatabase.getInstance(appContext) }
    val libraryRepository: LibraryRepository by lazy {
        RoomLibraryRepository(database, io, QueueStateFileStore(appContext)::read)
    }
    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(database, io) }
    internal val artworkPreviewRepository: ArtworkPreviewRepository by lazy {
        ArtworkPreviewRepository(appContext)
    }
    internal val credentialsProvider: CredentialsProvider by lazy {
        VaultCredentialsProvider(appContext)
    }
    internal val playbackConnection: PlaybackConnection by lazy {
        AndroidPlaybackConnection(appContext, main)
    }
    internal val backgroundWorkLauncher: BackgroundWorkLauncher by lazy { AndroidBackgroundWorkLauncher(appContext) }

    @Suppress("SpreadOperator") // Android Resources.getString принимает varargs.
    internal val settingsText = SettingsText { id, args -> appContext.getString(id, *args) }
    internal val libraryIndexingGateway: LibraryIndexingGateway by lazy {
        AndroidLibraryIndexingGateway(this, settingsText)
    }
    internal val artworkSettingsGateway: ArtworkSettingsGateway by lazy {
        AndroidArtworkSettingsGateway(appContext, this)
    }
    internal val diagnosticsGateway: DiagnosticsGateway by lazy { AndroidDiagnosticsGateway(this) }

    internal fun accountSignOut(email: String): AccountSignOut = AccountSignOut(
        AndroidAccountSignOutPort(appContext, this, email),
    )
}

class EnoToneApplication : Application() {
    private lateinit var processNetworkMonitor: NetworkMonitor

    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        // AppContainer creates the process-owned network callback once, outside Activity lifetime.
        processNetworkMonitor = container.networkMonitor
        // Живёт столько же, сколько процесс; держит только числа и состояния служб.
        LibraryDataRevision.watch(MainScope())
    }

    override fun onTerminate() {
        // Android releases process callbacks when the process exits; this explicit path also
        // makes teardown deterministic in environments that call onTerminate (including tests).
        processNetworkMonitor.close()
        super.onTerminate()
    }
}
