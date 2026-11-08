package app.enotone.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

/**
 * Streaming DataSource for one selected file, not the 8 MiB metadata reader.
 * Every connection is same-origin HTTPS, sends an exact Range, follows NO
 * redirects and accepts only a matching HTTP 206 / Content-Range response.
 */
@OptIn(UnstableApi::class)
internal class WebDavMedia3DataSource(
    private val authorization: String,
    private val remoteSize: Long,
    remoteEtag: String?,
) : BaseDataSource(true) {
    private val strongEtag = remoteEtag?.takeIf {
        it.length in 2..256 && it.first() == '"' && it.last() == '"' &&
            it.all { char -> char.code in 32..126 }
    }
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
        require(remoteSize > 0 && dataSpec.position in 0 until remoteSize &&
            dataSpec.length != 0L) { "Invalid playback position" }

        val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            remoteSize - 1
        } else {
            val bounded = minOf(dataSpec.length, remoteSize - dataSpec.position)
            require(bounded > 0) { "Invalid playback length" }
            dataSpec.position + bounded - 1
        }
        val count = end - dataSpec.position + 1
        transferInitializing(dataSpec)
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
                throw IOException("Playback HTTP status rejected")
            }
            WebDavPlaybackRange.count(conn.getHeaderField("Content-Range"),
                dataSpec.position, end, remoteSize)
            val encoding = conn.getHeaderField("Content-Encoding")
            if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
                throw IOException("Playback encoded response rejected")
            }
            if (strongEtag != null) {
                val received = conn.getHeaderField("ETag")
                if (received != null && received != strongEtag) {
                    throw IOException("Playback version changed")
                }
            }
            val declared = conn.getHeaderField("Content-Length")?.toLongOrNull()
            if (declared != null && declared != count) {
                throw IOException("Playback body length mismatch")
            }
            stream = conn.inputStream
            connection = conn
            bytesLeft = count
            uri = dataSpec.uri
            opened = true
            transferStarted(dataSpec)
            return count
        } catch (_: Exception) {
            try { conn?.inputStream?.close() } catch (_: Exception) { }
            conn?.disconnect()
            // Never propagate exception text: network libraries may include private URLs.
            throw IOException("WebDAV playback open rejected")
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
            throw IOException("WebDAV playback read failed")
        }
        if (received <= 0) throw IOException("Truncated playback body")
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
        private val remoteSize: Long,
        private val etag: String?,
    ) : DataSource.Factory {
        private val auth: String

        init {
            require(email.contains('@') && appPassword.isNotBlank()) {
                "Playback credentials required"
            }
            val raw = (email.trim() + ":" + appPassword)
                .toByteArray(StandardCharsets.UTF_8)
            auth = try { "Basic " + Base64.getEncoder().encodeToString(raw) }
                finally { raw.fill(0) }
            require(remoteSize > 0) { "Unknown playback size" }
        }

        override fun createDataSource(): DataSource =
            WebDavMedia3DataSource(auth, remoteSize, etag)
    }
}
