package app.enotone.android

import app.enotone.database.RemoteTrackEntity
import app.enotone.library.AndroidAudioTagReader
import app.enotone.library.FastAudioTagReader
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.LibraryTagStore
import app.enotone.library.WebDavRangeMediaDataSource
import app.enotone.library.WebDavRangeReader
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Ограниченные диагностические чтения одного трека; в отчёт попадают только коды и числа. */
internal class SingleTrackDiagnostics(private val container: AppContainer, private val text: SettingsText) {
    suspend fun platform(login: StoredWebDavLogin, testFile: String): String {
        val email = login.email
        val password = login.password
        var report: String
        var tagStage = "LOCAL_DB"
        var rangeReader: WebDavRangeReader? = null
        try {
            val result = withContext(container.io) {
                val database = container.database
                val selected = run {
                    val sourceKey = LibrarySnapshotPlanner.accountKey(email)
                    val available = database.remoteTrackDao().allForSource(sourceKey)
                        .filter { it.missingScans == 0 && (it.remoteSize ?: 0L) > 0L }
                    if (testFile.isBlank()) {
                        // Exactly ONE track (MP3 preferred), never a bulk scan.
                        available.sortedWith(
                            compareBy<RemoteTrackEntity> { !it.fileName.endsWith(".mp3", true) }
                                .thenBy { it.id }
                        ).firstOrNull()
                    } else {
                        val path = testFile.trim()
                        val name = path.removePrefix("/AllMusic/")
                        if (!path.startsWith("/AllMusic/") || '/' in name || name.isBlank()) null
                        else available.singleOrNull {
                            it.fileName == Normalizer.normalize(name, Normalizer.Form.NFC)
                        }
                    }
                }
                if (selected == null) {
                    intArrayOf(-1, 0, 0, 0, 0, 0, 0)
                } else {
                    tagStage = "SOURCE"
                    val reader = WebDavRangeReader(
                        encodedPath = selected.encodedPath,
                        fileSize = selected.remoteSize!!,
                        email = email,
                        appPassword = password,
                        etag = selected.remoteEtag,
                    )
                    rangeReader = reader
                    val source = WebDavRangeMediaDataSource(reader)
                    try {
                        // Separate an HTTP/Range failure from a platform extractor failure.
                        // Exactly one 64-KiB block at most; reused by the retriever.
                        tagStage = "RANGE"
                        if (source.readAt(0, ByteArray(1), 0, 1) != 1) {
                            throw java.io.IOException("Range probe unavailable")
                        }
                        tagStage = "EXTRACTOR"
                        val tags = AndroidAudioTagReader().read(source, selected.fileName)
                        val stats = reader.stats()
                        // Persist only if Room still contains the same file version.
                        tagStage = "STORE"
                        val writeDatabase = container.database
                        val persisted = run {
                            LibraryTagStore(writeDatabase).saveIfUnchanged(
                                email, selected, tags)
                        }
                        intArrayOf(1,
                            if (tags.title != null) 1 else 0,
                            if (tags.artist != null) 1 else 0,
                            if (tags.album != null) 1 else 0,
                            stats.requests, stats.downloadedBytes.toInt(),
                            if (persisted) 1 else 0,
                            stats.cacheRevisitHits, stats.cacheEvictions)
                    } finally {
                        source.close()
                    }
                }
            }
            report = if (result[0] < 0) text.get(R.string.one_tag_missing) else String.format(
                Locale.getDefault(), text.get(R.string.one_tag_report),
                result[1], result[2], result[3], result[4], result[5], result[6],
                result[7], result[8])
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val stats = rangeReader?.stats()
            val reason = stats?.issue?.name
                ?: when (tagStage) {
                    "EXTRACTOR" -> "PLATFORM_EXTRACTOR"
                    "STORE" -> "LOCAL_DB_WRITE"
                    else -> "PREPARATION"
                }
            report = String.format(
                Locale.getDefault(), text.get(R.string.one_tag_failed_detail), tagStage, reason,
                stats?.httpStatus?.toString() ?: "—",
                stats?.requests ?: 0, stats?.downloadedBytes ?: 0L,
            )
            // Numbers only: never put Content-Range raw header, path or ETag in UI.
            stats?.rangeMismatch?.let { mismatch ->
                report += " " + String.format(
                    Locale.getDefault(), text.get(R.string.one_tag_range_mismatch),
                    mismatch.kind.name,
                    mismatch.expectedStart.toString(),
                    mismatch.expectedEnd.toString(),
                    mismatch.expectedTotal.toString(),
                    mismatch.receivedStart?.toString() ?: "—",
                    mismatch.receivedEnd?.toString() ?: "—",
                    mismatch.receivedTotal?.toString() ?: "—",
                )
            }
        }
        return report
    }

    suspend fun fast(login: StoredWebDavLogin, testFile: String): String {
        val email = login.email
        val password = login.password
        var report: String
        var stage = "LOCAL_DB"
        var activeReader: WebDavRangeReader? = null
        try {
            val outcome = withContext(container.io) {
                val database = container.database
                val selected = run {
                    val key = LibrarySnapshotPlanner.accountKey(email)
                    val candidates = database.remoteTrackDao().allForSource(key).filter {
                        it.missingScans == 0 &&
                            (it.remoteSize ?: 0L) > 0L &&
                            (it.fileName.endsWith(".mp3", true) ||
                             it.fileName.endsWith(".flac", true))
                    }
                    if (testFile.isBlank()) {
                        // Same stable MP3-first order as the older diagnostic.
                        candidates.sortedWith(
                            compareBy<RemoteTrackEntity> {
                                !it.fileName.endsWith(".mp3", true)
                            }.thenBy { it.id }
                        ).firstOrNull()
                    } else {
                        val path = testFile.trim()
                        val name = path.removePrefix("/AllMusic/")
                        if (!path.startsWith("/AllMusic/") ||
                            name.isBlank() || '/' in name) null
                        else candidates.singleOrNull {
                            it.fileName == Normalizer.normalize(name, Normalizer.Form.NFC)
                        }
                    }
                }
                if (selected == null) null else {
                    stage = "FAST_RANGE"
                    val reader = WebDavRangeReader(
                        encodedPath = selected.encodedPath,
                        fileSize = selected.remoteSize!!,
                        email = email, appPassword = password,
                        etag = selected.remoteEtag,
                    )
                    activeReader = reader
                    reader.use {
                        // No MediaMetadataRetriever fallback: this button verifies
                        // bounded ID3/FLAC metadata only, never audio frames.
                        val parsed = FastAudioTagReader().read(
                            selected.fileName, reader.fileSize, reader::readAt)
                        val stats = reader.stats()
                        var persisted = false
                        val extractedTags = parsed.tags
                        if (extractedTags != null) {
                            stage = "FAST_STORE"
                            val storeDatabase = container.database
                            persisted = run {
                                LibraryTagStore(storeDatabase).saveIfUnchanged(
                                    email, selected, extractedTags)
                            }
                        }
                        FastProbeResult(
                            parsed.code.name,
                            if (parsed.embeddedTitle) 1 else 0,
                            if (parsed.embeddedArtist) 1 else 0,
                            if (parsed.embeddedAlbum) 1 else 0,
                            stats.requests, stats.downloadedBytes,
                            if (persisted) 1 else 0,
                        )
                    }
                }
            }
            report = if (outcome == null) text.get(R.string.one_tag_missing) else
                String.format(Locale.getDefault(), text.get(R.string.fast_tag_report),
                    outcome.code, outcome.title, outcome.artist,
                    outcome.album, outcome.requests, outcome.bytes,
                    outcome.saved)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val stats = activeReader?.stats()
            val reason = stats?.issue?.name ?: "FAST_PROBE_ERROR"
            report = String.format(Locale.getDefault(), text.get(R.string.one_tag_failed_detail),
                stage, reason, stats?.httpStatus?.toString() ?: "—",
                stats?.requests ?: 0, stats?.downloadedBytes ?: 0L)
        }
        return report
    }
}

private data class FastProbeResult(
    val code: String,
    val title: Int,
    val artist: Int,
    val album: Int,
    val requests: Int,
    val bytes: Long,
    val saved: Int,
)
