package app.enotone.android

import app.enotone.database.RemoteTrackEntity
import app.enotone.library.AndroidAudioTagReader
import app.enotone.library.FastAudioTagReader
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.LibraryTagStore
import app.enotone.library.WebDavRangeMediaDataSource
import app.enotone.library.WebDavRangeReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.IOException

/** Ограниченные диагностические чтения одного трека; в отчёт попадают только коды и числа. */
internal class SingleTrackDiagnostics(private val container: AppContainer, private val text: SettingsText) {
    suspend fun platform(login: StoredWebDavLogin, testFile: String): String = probe(login, testFile, fast = false)
    suspend fun fast(login: StoredWebDavLogin, testFile: String): String = probe(login, testFile, fast = true)

    private suspend fun probe(login: StoredWebDavLogin, file: String,
        fast: Boolean): String = withContext(container.io) {
        val failure = TrackProbeFailure(text, fast)
        try {
            val rows = container.database.remoteTrackDao().allForSource(LibrarySnapshotPlanner.accountKey(login.email))
            val selected = DiagnosticTrackSelector.select(rows, file, fast)
            if (selected == null) {
                text.get(R.string.one_tag_missing)
            } else {
                failure.stage = if (fast) "FAST_RANGE" else "SOURCE"
                WebDavRangeReader(encodedPath = selected.encodedPath, fileSize = requireNotNull(selected.remoteSize),
                    email = login.email, appPassword = login.password, etag = selected.remoteEtag).use { reader ->
                    failure.reader = reader
                    if (fast) readFast(login, selected, reader, failure) else readPlatform(login, selected,
                        reader, failure)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failure.report()
        }
    }

    private suspend fun readPlatform(
        login: StoredWebDavLogin,
        selected: RemoteTrackEntity,
        reader: WebDavRangeReader,
        failure: TrackProbeFailure,
    ): String = WebDavRangeMediaDataSource(reader).use { source ->
        failure.stage = "RANGE"
        if (source.readAt(0, ByteArray(1), 0, 1) != 1) throw IOException("Range probe unavailable")
        failure.stage = "EXTRACTOR"
        val tags = AndroidAudioTagReader().read(source, selected.fileName)
        val stats = reader.stats()
        failure.stage = "STORE"
        val persisted = LibraryTagStore(container.database).saveIfUnchanged(login.email, selected, tags)
        text.get(R.string.one_tag_report, present(tags.title), present(tags.artist), present(tags.album),
            stats.requests, stats.downloadedBytes.toInt(), if (persisted) 1 else 0,
            stats.cacheRevisitHits, stats.cacheEvictions)
    }

    private suspend fun readFast(
        login: StoredWebDavLogin,
        selected: RemoteTrackEntity,
        reader: WebDavRangeReader,
        failure: TrackProbeFailure,
    ): String {
        val parsed = FastAudioTagReader().read(selected.fileName, reader.fileSize, reader::readAt)
        val stats = reader.stats()
        val tags = parsed.tags
        val persisted = if (tags == null) false else {
            failure.stage = "FAST_STORE"
            LibraryTagStore(container.database).saveIfUnchanged(login.email, selected, tags)
        }
        return text.get(R.string.fast_tag_report, parsed.code.name,
            if (parsed.embeddedTitle) 1 else 0, if (parsed.embeddedArtist) 1 else 0,
            if (parsed.embeddedAlbum) 1 else 0, stats.requests, stats.downloadedBytes, if (persisted) 1 else 0)
    }

    private fun present(value: String?): Int = if (value == null) 0 else 1
}
