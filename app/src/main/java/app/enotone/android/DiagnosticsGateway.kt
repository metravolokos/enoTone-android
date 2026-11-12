package app.enotone.android

import app.enotone.database.RemoteTrackEntity
import app.enotone.library.LibrarySnapshotStore
import app.enotone.library.LibraryTagStore
import app.enotone.library.PilotIndexProgress
import app.enotone.library.PilotTagIndexer
import app.enotone.network.FlatLibraryWebDavClient
import app.enotone.network.ProbeReport
import app.enotone.network.WebDavDiagnostics
import kotlinx.coroutines.withContext

internal enum class DiagnosticCommand { COUNT, SAVED, COVERAGE, PLATFORM_TAG, FAST_TAG, CHECKPOINT }
internal enum class AuditQuery { MISSING_ALBUM, MISSING_ALBUM_ARTIST, SEARCH }

internal interface DiagnosticsGateway {
    suspend fun inspect(login: StoredWebDavLogin, file: String, infinity: Boolean): ProbeReport
    suspend fun run(command: DiagnosticCommand, login: StoredWebDavLogin, file: String): String
    suspend fun audit(query: AuditQuery, email: String, search: String): List<RemoteTrackEntity>
    suspend fun queue(email: String, id: String?): Int
    suspend fun pilot(login: StoredWebDavLogin, count: Int, progress: (String) -> Unit): String
}

internal class AndroidDiagnosticsGateway(
    private val container: AppContainer,
    private val text: SettingsText,
) : DiagnosticsGateway {
    override suspend fun inspect(login: StoredWebDavLogin, file: String, infinity: Boolean): ProbeReport =
        withContext(container.io) { WebDavDiagnostics().inspect(login.email, login.password, file, infinity) }

    override suspend fun run(command: DiagnosticCommand, login: StoredWebDavLogin, file: String): String =
        withContext(container.io) {
            when (command) {
                DiagnosticCommand.COUNT -> count(login)
                DiagnosticCommand.SAVED -> text.get(R.string.local_saved_report,
                    LibrarySnapshotStore(container.database).presentCount(login.email))
                DiagnosticCommand.COVERAGE -> coverage(login.email)
                DiagnosticCommand.PLATFORM_TAG -> SingleTrackDiagnostics(container, text).platform(login, file)
                DiagnosticCommand.FAST_TAG -> SingleTrackDiagnostics(container, text).fast(login, file)
                DiagnosticCommand.CHECKPOINT -> checkpoint(login.email)
            }
        }

    private suspend fun count(login: StoredWebDavLogin): String = FlatLibraryWebDavClient().use { client ->
        val listing = client.list(login.email, login.password)
        text.get(R.string.file_count_report, listing.tracks.size, listing.skippedDirectories,
            listing.skippedNested, listing.skippedNonAudio, listing.invalidEntries)
    }

    private suspend fun coverage(email: String): String {
        val coverage = LibraryTagStore(container.database).storedCoverage(email)
        return text.get(R.string.local_tag_coverage_report, coverage.presentTracks,
            coverage.tracksWithAnyTag, coverage.tracksWithTitle, coverage.tracksWithArtist,
            coverage.tracksWithAlbum, coverage.tracksWithAlbumArtist)
    }

    private suspend fun checkpoint(email: String): String {
        val stored = PilotTagIndexer(container.database).offlineCheckpoint(email)
        return text.get(R.string.pilot_offline_report, stored.successful, stored.retryableErrors,
            stored.permanentErrors, stored.notAttempted)
    }

    override suspend fun audit(query: AuditQuery, email: String, search: String): List<RemoteTrackEntity> =
        withContext(container.io) {
            val store = LibraryTagStore(container.database)
            when (query) {
                AuditQuery.MISSING_ALBUM -> store.missingAlbum(email)
                AuditQuery.MISSING_ALBUM_ARTIST -> store.missingAlbumArtist(email)
                AuditQuery.SEARCH -> store.findTracks(email, search)
            }
        }

    override suspend fun queue(email: String, id: String?): Int = withContext(container.io) {
        val store = LibraryTagStore(container.database)
        if (id == null) store.queueAllForRefresh(email) else if (store.queueOneForRefresh(email, id)) 1 else 0
    }

    override suspend fun pilot(login: StoredWebDavLogin, count: Int, progress: (String) -> Unit): String =
        withContext(container.io) {
            val result = PilotTagIndexer(container.database).run(login.email, login.password, count,
                onProgress = { current -> progress(pilotText(current)) })
            pilotText(result)
        }

    private fun pilotText(progress: PilotIndexProgress): String = text.get(R.string.pilot_report,
        progress.requested, progress.attempted, progress.savedWithTags,
        progress.completedWithoutTags, progress.retryableErrors, progress.staleWrites,
        progress.getRequests, progress.downloadedBytes, progress.remaining,
        progress.stopCode, progress.permanentErrors) +
        if (progress.lastErrorStage == "NONE") "" else "\n" + text.get(R.string.pilot_failure_detail,
            progress.lastErrorStage, progress.lastErrorReason,
            if (progress.lastErrorHttpStatus == 0) "—" else progress.lastErrorHttpStatus.toString())
}
