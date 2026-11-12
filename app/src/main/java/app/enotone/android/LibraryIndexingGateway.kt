package app.enotone.android

import app.enotone.library.LibraryReindexCoordinator
import app.enotone.library.LibraryReindexScope
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.LibrarySnapshotStore
import app.enotone.library.SnapshotPlan
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
            invalidateChangedArtwork(login.email, plan)
            // Новые и изменённые треки видны открытым экранам сразу, ещё до чтения тегов.
            LibraryDataRevision.bump()
            val pending = container.database.remoteTrackDao()
                .pendingFastScans(LibrarySnapshotPlanner.accountKey(login.email)).size
            LibraryImportResult(text.get(R.string.local_import_report,
                listing.tracks.size, store.presentCount(login.email),
                plan.inserted, plan.changed, plan.unchanged), pending)
        }
    }

    /**
     * Аудит п. 5: ключ обложки — ETag и размер; файл, изменившийся только по mtime, сохранил бы
     * старую обложку. Сбрасываем ссылки только изменённых файлов: ключи прежние, кэш не перекачивается.
     */
    private suspend fun invalidateChangedArtwork(email: String, plan: SnapshotPlan) {
        if (plan.changedIds.isEmpty()) return
        val changed = plan.changedIds.toHashSet()
        val targets = plan.upserts.filter { it.id in changed }.mapNotNull { row ->
            row.remoteSize?.let { size -> PlayingArtworkTarget(row.id, row.encodedPath, size, row.remoteEtag) }
        }
        try {
            container.artworkPreviewRepository.invalidate(email, targets)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Список уже сохранён; обложка обновится при следующей переиндексации.
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
