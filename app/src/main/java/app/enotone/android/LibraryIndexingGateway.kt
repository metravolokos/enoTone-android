package app.enotone.android

import app.enotone.library.LibraryReindexCoordinator
import app.enotone.library.LibraryReindexScope
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.LibrarySnapshotStore
import app.enotone.network.FlatLibraryWebDavClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

internal data class LibraryImportResult(val report: String, val pendingTags: Int)

internal interface LibraryIndexingGateway {
    suspend fun update(login: StoredWebDavLogin): LibraryImportResult
    suspend fun reindex(login: StoredWebDavLogin): Int
}

internal class AndroidLibraryIndexingGateway(
    private val container: AppContainer,
    private val text: SettingsText,
) : LibraryIndexingGateway {
    override suspend fun update(login: StoredWebDavLogin): LibraryImportResult = withContext(container.io) {
        FlatLibraryWebDavClient().use { client ->
            val listing = client.list(login.email, login.password)
            val store = LibrarySnapshotStore(container.database)
            val plan = store.save(login.email, listing)
            val pending = container.database.remoteTrackDao()
                .pendingFastScans(LibrarySnapshotPlanner.accountKey(login.email)).size
            LibraryImportResult(text.get(R.string.local_import_report,
                listing.tracks.size, store.presentCount(login.email),
                plan.inserted, plan.changed, plan.unchanged), pending)
        }
    }

    override suspend fun reindex(login: StoredWebDavLogin): Int = withContext(container.io) {
        val selected = LibraryReindexCoordinator(container.database)
            .queue(login.email, LibraryReindexScope.EntireLibrary)
        if (selected.queued > 0) {
            val targets = selected.artworkSources.map { item ->
                PlayingArtworkTarget(item.id, item.encodedPath, item.remoteSize, item.remoteEtag)
            }
            try {
                container.artworkPreviewRepository.invalidate(login.email, targets)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Очередь тегов уже сохранена; необязательная обложка не блокирует индексатор.
            }
        }
        selected.queued
    }
}
