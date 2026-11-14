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
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
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
    ) : DataSource.Factory {
        private val auth: String
        private val files: MutableMap<String, PlaybackRemoteFile> = ConcurrentHashMap()

        init {
            require(email.contains('@') && appPassword.isNotBlank()) {
                "Playback credentials required"
            }
            files.putAll(WebDavPlaylistRoutes.index(tracks))
            val raw = (email.trim() + ":" + appPassword)
                .toByteArray(StandardCharsets.UTF_8)
            auth = try { "Basic " + Base64.getEncoder().encodeToString(raw) }
                finally { raw.fill(0) }
        }

        /** All already-created data sources see new per-file size and ETag routes. */
        fun addTracks(tracks: List<QueueTrack>) {
            if (tracks.isEmpty()) return
            val added = WebDavPlaylistRoutes.index(tracks)
            require(added.keys.none { it in files }) { "Playback URL already exists" }
            files.putAll(added)
        }

        override fun createDataSource(): DataSource =
            WebDavMedia3DataSource(auth, files)
    }
}
