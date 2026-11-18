package app.enotone.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.upstream.Allocator
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection

/**
 * Streaming DataSource for one selected file, not the 8 MiB metadata reader.
 * Every connection is same-origin HTTPS, sends an exact Range, follows NO
 * redirects and accepts only a matching HTTP 206 / Content-Range response.
 */
@OptIn(UnstableApi::class)
/**
 * Each open resolves its own file metadata from the validated RAM-only playlist.
 * This avoids accidentally reusing the previous track's size/ETag on skip.
 */
internal class WebDavMedia3DataSource(
    private val authorization: String,
    private val files: Map<String, PlaybackRemoteFile>,
) : BaseDataSource(true) {
    private var connection: HttpsURLConnection? = null
    private var stream: InputStream? = null
    private var uri: Uri? = null
    private var bytesLeft = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        require(!opened) { "Playback source already opened" }
        require(dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET) { "Only GET allowed" }
        val safeUrl = WebDavPlaybackTarget.url(dataSpec.uri.encodedPath ?: "")
        require(dataSpec.uri.toString() == safeUrl) { "Playback URI mismatch" }
        val remote = files[safeUrl] ?: throw IOException("Playback file not in queue")
        val remoteSize = remote.size
        val strongEtag = remote.etag?.takeIf {
            it.length in 2..256 && it.first() == '"' && it.last() == '"' &&
                it.all { char -> char.code in 32..126 }
        }
        // Media3 DataSource contract: an open *at* EOF is valid and read()
        // must return RESULT_END_OF_INPUT. The previous strict `until size`
        // check threw an unspecified IO error near a track boundary.
        val window = PlaybackReadWindow.resolve(
            remoteSize, dataSpec.position, dataSpec.length,
        ) ?: throw DataSourceException(
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        )
        transferInitializing(dataSpec)
        if (window.endInclusive == null) {
            // No invalid bytes=size-... Range, no network, no ETag exposure.
            uri = dataSpec.uri
            bytesLeft = 0L
            opened = true
            transferStarted(dataSpec)
            return window.openReturnLength
        }
        val end = window.endInclusive
        val count = window.bytesAvailable
        var conn: HttpsURLConnection? = null
        try {
            conn = URL(safeUrl).openConnection() as HttpsURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8_000
            conn.readTimeout = 15_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", authorization)
            conn.setRequestProperty("Range", "bytes=${dataSpec.position}-$end")
            conn.setRequestProperty("Accept-Encoding", "identity")
            if (strongEtag != null) conn.setRequestProperty("If-Range", strongEtag)
            if (conn.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw PlaybackDataIssueException(PlaybackDataIssue.OPEN_STATUS)
            }
            try {
                WebDavPlaybackRange.count(conn.getHeaderField("Content-Range"),
                    dataSpec.position, end, remoteSize)
            } catch (_: IOException) {
                throw PlaybackDataIssueException(PlaybackDataIssue.OPEN_RANGE)
            }
            val encoding = conn.getHeaderField("Content-Encoding")
            if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
                throw PlaybackDataIssueException(PlaybackDataIssue.OPEN_ENCODING)
            }
            if (strongEtag != null) {
                val received = conn.getHeaderField("ETag")
                if (received != null && received != strongEtag) {
                    throw PlaybackDataIssueException(PlaybackDataIssue.OPEN_VERSION)
                }
            }
            val declared = conn.getHeaderField("Content-Length")?.toLongOrNull()
            if (declared != null && declared != count) {
                throw PlaybackDataIssueException(PlaybackDataIssue.OPEN_LENGTH)
            }
            stream = conn.inputStream
            connection = conn
            bytesLeft = count
            uri = dataSpec.uri
            opened = true
            transferStarted(dataSpec)
            return window.openReturnLength
        } catch (failure: Exception) {
            try { conn?.inputStream?.close() } catch (_: Exception) { }
            conn?.disconnect()
            // Do not wrap the original IOException as a cause: it may reveal
            // private URLs or authorization data through a library exception.
            if (failure is PlaybackDataIssueException) throw failure
            throw PlaybackDataIssueException(PlaybackDataIssue.OPEN_NETWORK)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset <= buffer.size &&
            length <= buffer.size - offset) { "Invalid audio buffer" }
        if (length == 0) return 0
        if (bytesLeft == 0L) return C.RESULT_END_OF_INPUT
        val input = stream ?: throw IOException("Playback source closed")
        val received = try {
            input.read(buffer, offset, minOf(length.toLong(), bytesLeft).toInt())
        } catch (_: Exception) {
            throw PlaybackDataIssueException(PlaybackDataIssue.READ_NETWORK)
        }
        if (received <= 0) {
            throw PlaybackDataIssueException(PlaybackDataIssue.READ_TRUNCATED)
        }
        bytesLeft -= received
        bytesTransferred(received)
        return received
    }

    override fun getUri(): Uri? = uri

    /** Never expose response headers such as ETag or server identifiers. */
    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun close() {
        try {
            stream?.close()
        } catch (_: Exception) {
            // Best effort release; never expose the URL in a close exception.
        } finally {
            stream = null
            connection?.disconnect()
            connection = null
            uri = null
            bytesLeft = 0
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(
        email: String,
        appPassword: String,
        tracks: List<QueueTrack>,
        private val dataSourceFactoryOverride: DataSource.Factory? = null,
    ) : DataSource.Factory {
        private val auth: String
        private val files: MutableMap<String, PlaybackRemoteFile> = ConcurrentHashMap()
        private val routeLock = Any()
        private val trackUrls = mutableMapOf<String, String>()
        private val activeTrackIds = mutableSetOf<String>()
        private val mediaPeriodRefs = mutableMapOf<String, Int>()

        init {
            require(email.contains('@') && appPassword.isNotBlank()) {
                "Playback credentials required"
            }
            files.putAll(WebDavPlaylistRoutes.index(tracks))
            require(tracks.map { it.id }.distinct().size == tracks.size) { "Duplicate playback track ID" }
            tracks.forEach { trackUrls[it.id] = WebDavPlaybackTarget.url(it.encodedPath) }
            activeTrackIds += trackUrls.keys
            val raw = (email.trim() + ":" + appPassword)
                .toByteArray(StandardCharsets.UTF_8)
            auth = try { "Basic " + Base64.getEncoder().encodeToString(raw) }
                finally { raw.fill(0) }
        }

        /** Re-adding the same track is idempotent; live URL metadata stays immutable. */
        fun addTracks(tracks: List<QueueTrack>) {
            if (tracks.isEmpty()) return
            val added = WebDavPlaylistRoutes.index(tracks)
            require(tracks.map { it.id }.distinct().size == tracks.size) { "Duplicate playback track ID" }
            synchronized(routeLock) {
                pruneUnreferencedRoutes()
                tracks.forEach { track ->
                    val url = WebDavPlaybackTarget.url(track.encodedPath)
                    val previousUrl = trackUrls[track.id]
                    require(previousUrl == null || previousUrl == url) { "Playback track route changed" }
                    val route = added.getValue(url)
                    val existing = files[url]
                    if (existing != null && existing != route) {
                        require(!isRouteRetained(url)) { "Playback URL already exists" }
                    }
                    files[url] = route
                    trackUrls[track.id] = url
                }
            }
        }

        /** Update the queue-owned route set after Media3 accepts a timeline edit. */
        internal fun setActiveTrackIds(trackIds: Collection<String>) {
            require(trackIds.size == trackIds.toSet().size) { "Duplicate playback track ID" }
            synchronized(routeLock) {
                require(trackIds.all(trackUrls::containsKey)) { "Missing playback route" }
                activeTrackIds.clear()
                activeTrackIds.addAll(trackIds)
                pruneUnreferencedRoutes()
            }
        }

        /** Hold an evicted route until Media3 releases its active MediaPeriod. */
        internal fun trackMediaSource(trackId: String, source: MediaSource): MediaSource {
            val url = synchronized(routeLock) {
                checkNotNull(trackUrls[trackId]) { "Missing playback route" }
            }
            return RouteLifetimeMediaSource(
                source,
                onPeriodCreated = { retainPeriod(url) },
                onPeriodReleased = { releasePeriod(url) },
            )
        }

        internal val routeCount: Int
            get() = synchronized(routeLock) { files.size }

        override fun createDataSource(): DataSource = dataSourceFactoryOverride?.createDataSource()
            ?: WebDavMedia3DataSource(auth, files)

        private fun retainPeriod(url: String): Boolean = synchronized(routeLock) {
            if (!files.containsKey(url)) return@synchronized false
            mediaPeriodRefs[url] = (mediaPeriodRefs[url] ?: 0) + 1
            true
        }

        private fun releasePeriod(url: String) {
            synchronized(routeLock) {
                val refs = mediaPeriodRefs[url] ?: return@synchronized
                if (refs == 1) mediaPeriodRefs.remove(url) else mediaPeriodRefs[url] = refs - 1
                pruneUnreferencedRoutes()
            }
        }

        private fun isRouteRetained(url: String): Boolean =
            url in mediaPeriodRefs || activeTrackIds.any { trackUrls[it] == url }

        private fun pruneUnreferencedRoutes() {
            val retainedUrls = activeTrackIds.mapNotNull(trackUrls::get).toSet() + mediaPeriodRefs.keys
            val retainedIds = activeTrackIds + trackUrls.filterValues { it in retainedUrls }.keys
            files.keys.toList().filterNot(retainedUrls::contains).forEach(files::remove)
            trackUrls.keys.toList().filterNot(retainedIds::contains).forEach(trackUrls::remove)
        }
    }
}

@OptIn(UnstableApi::class)
private class RouteLifetimeMediaSource(
    source: MediaSource,
    private val onPeriodCreated: () -> Boolean,
    private val onPeriodReleased: () -> Unit,
) : WrappingMediaSource(source) {
    private val retainedPeriodsLock = Any()
    private val retainedPeriods = IdentityHashMap<MediaPeriod, Int>()

    override fun createPeriod(
        id: MediaSource.MediaPeriodId,
        allocator: Allocator,
        startPositionUs: Long,
    ): MediaPeriod {
        val retained = onPeriodCreated()
        var created = false
        return try {
            val period = super.createPeriod(id, allocator, startPositionUs)
            if (retained) {
                synchronized(retainedPeriodsLock) {
                    retainedPeriods[period] = (retainedPeriods[period] ?: 0) + 1
                }
            }
            created = true
            period
        } finally {
            if (!created && retained) onPeriodReleased()
        }
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        val retained = synchronized(retainedPeriodsLock) {
            val refs = retainedPeriods[mediaPeriod] ?: return@synchronized false
            if (refs == 1) retainedPeriods.remove(mediaPeriod) else retainedPeriods[mediaPeriod] = refs - 1
            true
        }
        try {
            super.releasePeriod(mediaPeriod)
        } finally {
            if (retained) onPeriodReleased()
        }
    }
}
