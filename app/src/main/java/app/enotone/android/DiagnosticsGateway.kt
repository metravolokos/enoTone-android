package app.enotone.android

import app.enotone.database.RemoteTrackEntity
import app.enotone.library.LibraryTagStore
import app.enotone.network.ProbeReport
import app.enotone.network.WebDavDiagnostics
import kotlinx.coroutines.withContext

internal enum class AuditQuery { MISSING_ALBUM, MISSING_ALBUM_ARTIST }

internal interface DiagnosticsGateway {
    suspend fun inspect(login: StoredWebDavLogin): ProbeReport
    suspend fun audit(query: AuditQuery, email: String): List<RemoteTrackEntity>
}

internal class AndroidDiagnosticsGateway(
    private val container: AppContainer,
) : DiagnosticsGateway {
    override suspend fun inspect(login: StoredWebDavLogin): ProbeReport =
        withContext(container.io) { WebDavDiagnostics().inspect(login.email, login.password) }

    override suspend fun audit(query: AuditQuery, email: String): List<RemoteTrackEntity> =
        withContext(container.io) {
            val store = LibraryTagStore(container.database)
            when (query) {
                AuditQuery.MISSING_ALBUM -> store.missingAlbum(email)
                AuditQuery.MISSING_ALBUM_ARTIST -> store.missingAlbumArtist(email)
            }
        }
}
