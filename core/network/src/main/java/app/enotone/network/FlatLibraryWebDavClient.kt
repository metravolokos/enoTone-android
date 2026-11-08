package app.enotone.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Safe to display: never includes usernames, remote file names, paths, or headers. */
class FlatWebDavHttpError(val statusCode: Int) : IOException("WebDAV list failed: HTTP " + statusCode)

/**
 * One network request to enumerate immediate entries of /AllMusic.
 * No recursive PROPFIND, no persistence, no logging, no redirects.
 *
 * Call close() after use. Credentials are held in memory only for the duration of list().
 * M1 initial client: temporary 16 MiB XML cap until the Room write pipeline is connected.
 */
class FlatLibraryWebDavClient(
    private val rootUrl: String = "https://webdav.cloud.mail.ru/AllMusic/",
    private val allowLoopbackHttpForTests: Boolean = false,
    private val client: HttpClient = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = 40_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    },
) : Closeable {
    init {
        val uri = URI(rootUrl)
        val testHttp = allowLoopbackHttpForTests && uri.scheme == "http" &&
            uri.host in setOf("localhost", "127.0.0.1", "::1")
        require(uri.scheme == "https" || testHttp) { "HTTPS required" }
        require(uri.host != null && uri.userInfo == null && uri.query == null &&
            uri.fragment == null && uri.rawPath.trimEnd('/') == "/AllMusic") {
            "Invalid library endpoint"
        }
    }

    suspend fun list(email: String, appPassword: String): FlatWebDavListing = withContext(Dispatchers.IO) {
        require(email.isNotBlank() && appPassword.isNotBlank()) { "Missing credentials" }
        val credentialBytes = (email.trim() + ":" + appPassword).toByteArray(StandardCharsets.UTF_8)
        val auth = try {
            "Basic " + Base64.getEncoder().encodeToString(credentialBytes)
        } finally {
            credentialBytes.fill(0)
        }
        try {
            val response = client.request(rootUrl) {
                method = HttpMethod("PROPFIND")
                header(HttpHeaders.Authorization, auth)
                header("Depth", "1")
                val xml = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getetag/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
                setBody(TextContent(xml, ContentType.Application.Xml))
            }
            if (response.status.value != 207) {
                throw FlatWebDavHttpError(response.status.value)
            }
            // Bounded read: even a malicious server cannot allocate an unbounded XML body.
            val channel = response.bodyAsChannel()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = channel.readAvailable(buffer)
                if (count == -1) break
                if (count == 0) continue
                if (output.size() > MAX_XML_BYTES - count) {
                    throw IOException("WebDAV XML exceeds temporary size limit")
                }
                output.write(buffer, 0, count)
            }
            FlatWebDavListingParser(rootUrl).parse(ByteArrayInputStream(output.toByteArray()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: FlatWebDavHttpError) {
            throw e
        } catch (_: Exception) {
            // Never expose upstream exception messages: URL/user details may be embedded.
            throw IOException("WebDAV listing failed; inspect sanitized HTTP status")
        }
    }

    override fun close() {
        client.close()
    }

    private companion object {
        const val MAX_XML_BYTES = 16 * 1024 * 1024
    }
}
