package app.enotone.android

import android.app.Application
import android.content.Context
import app.enotone.database.EnoToneDatabase
import app.enotone.library.LibraryRepository
import app.enotone.library.RoomLibraryRepository
import app.enotone.playback.QueueStateFileStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Application-scope manual wiring (ADR-0077). No ViewModel or secrets are retained here. */
class AppContainer(
    context: Context,
    val io: CoroutineDispatcher = Dispatchers.IO,
    val main: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val appContext = context.applicationContext
    val database: EnoToneDatabase by lazy { EnoToneDatabase.getInstance(appContext) }
    val libraryRepository: LibraryRepository by lazy {
        RoomLibraryRepository(database, io, QueueStateFileStore(appContext)::read)
    }
    internal val artworkPreviewRepository: ArtworkPreviewRepository by lazy {
        ArtworkPreviewRepository(appContext)
    }
    internal val credentialsProvider: CredentialsProvider by lazy {
        VaultCredentialsProvider(appContext)
    }
    internal val playbackConnection: PlaybackConnection by lazy {
        AndroidPlaybackConnection(appContext, main)
    }
    internal fun accountSignOut(email: String): AccountSignOut = AccountSignOut(
        AndroidAccountSignOutPort(appContext, this, email),
    )
}

class EnoToneApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }
}
