package app.enotone.android

import android.content.Context
import app.enotone.library.LibrarySnapshotPlanner
import kotlinx.coroutines.withContext

internal interface ArtworkSettingsGateway {
    fun mode(): ArtworkLoadMode
    fun saveMode(mode: ArtworkLoadMode)
    suspend fun tagsReady(login: StoredWebDavLogin): Boolean
    suspend fun needsPreparation(login: StoredWebDavLogin): Boolean
    suspend fun storedBytes(): Long
    suspend fun clear()
}

internal class AndroidArtworkSettingsGateway(
    private val context: Context,
    private val container: AppContainer,
) : ArtworkSettingsGateway {
    private val preferences = ArtworkPreferences(context)
    override fun mode() = preferences.mode()
    override fun saveMode(mode: ArtworkLoadMode) = preferences.setMode(mode)
    override suspend fun tagsReady(login: StoredWebDavLogin): Boolean = withContext(container.io) {
        container.database.remoteTrackDao()
            .pendingFastScans(LibrarySnapshotPlanner.accountKey(login.email)).isEmpty()
    }
    override suspend fun needsPreparation(login: StoredWebDavLogin): Boolean = withContext(container.io) {
        ArtworkIndexPlanner.needsPreparation(context, login.email)
    }
    override suspend fun storedBytes(): Long = withContext(container.io) {
        container.artworkPreviewRepository.storedBytes()
    }
    override suspend fun clear() = withContext(container.io) {
        container.artworkPreviewRepository.clearAll()
    }
}
