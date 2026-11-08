package app.enotone.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** Contains no remote names, URLs, login, passwords, token, or HTTP header values. */
data class ProbeStep(
    val operation: String,
    val httpStatus: Int?,
    val elapsedMs: Long,
    val etagPresent: Boolean,
    val rangeAdvertised: Boolean,
    val note: String,
)
data class ProbeReport(val steps: List<ProbeStep>) {
    /** Only whitelisted diagnostic metadata is ever exported. */
    fun toSanitizedText(): String = steps.joinToString("\n") { step ->
        "${step.operation}: HTTP ${step.httpStatus ?: "—"}; ${step.elapsedMs} мс; " +
            "ETag: ${if (step.etagPresent) "есть" else "нет"}; " +
            "Accept-Ranges bytes: ${if (step.rangeAdvertised) "есть" else "нет"}; ${step.note}"
    }
}

/**
 * M0-only WebDAV probe. Credentials live in RAM for the duration of inspect().
 * No redirects, response-body parsing, logging, or credential persistence.
 *
 * Ktor CIO replaces OkHttp ONLY in this diagnostic spike: see ADR-0013.
 */
class WebDavDiagnostics(
    private val baseUrl: String = "https://webdav.cloud.mail.ru/",
    private val allowLoopbackHttpForTests: Boolean = false,
) {
    init {
        val uri = URI(baseUrl)
        val allowedTestHttp = allowLoopbackHttpForTests &&
            uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "::1")
        require(uri.scheme == "https" || allowedTestHttp) { "Only HTTPS is allowed" }
        require(uri.host != null && uri.userInfo == null && uri.query == null &&
            uri.fragment == null && (uri.path == "/" || uri.path.isNullOrEmpty()))
    }

    fun inspect(email: String, password: String, testFile: String = "", probeInfinity: Boolean = false): ProbeReport {
        require(email.isNotBlank() && password.isNotBlank())
        val credentials = (email.trim() + ":" + password).toByteArray(StandardCharsets.UTF_8)
        val auth = "Basic " + Base64.getEncoder().encodeToString(credentials)
        credentials.fill(0)
        val client = HttpClient(CIO) {
            followRedirects = false
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
        }
        try {
            val steps = mutableListOf<ProbeStep>()
            steps += request(client, "PROPFIND /", endpoint(emptyList()), auth, depth = "1")
            steps += request(client, "PROPFIND /AllMusic", endpoint(listOf("AllMusic")), auth, depth = "1")
            if (probeInfinity) {
                steps += request(client, "PROPFIND Depth infinity", endpoint(listOf("AllMusic")), auth, depth = "infinity")
            } else {
                steps += skipped("PROPFIND Depth infinity", "Не запрошено")
            }
            val parts = testFile.trim().trim('/').split('/').filter { it.isNotBlank() }
            if (parts.size >= 2 && parts.first() == "AllMusic" && parts.none { it == "." || it == ".." }) {
                steps += request(client, "GET Range 0-0", endpoint(parts), auth, ranged = true)
            } else {
                steps += skipped("GET Range 0-0", "Не проверено: нужен путь /AllMusic/…")
            }
            return ProbeReport(steps)
        } finally {
            client.close()
        }
    }

    private fun skipped(name: String, note: String): ProbeStep =
        ProbeStep(name, null, 0, false, false, note)

    // A path segment is encoded independently: embedded '?'/'#'/'%' cannot become URL controls.
    private fun endpoint(segments: List<String>): String {
        val path = segments.joinToString("/") {
            URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20")
        }
        return baseUrl.trimEnd('/') + "/" + path
    }

    private fun request(
        client: HttpClient, name: String, url: String, auth: String, depth: String? = null,
        ranged: Boolean = false,
    ): ProbeStep {
        val started = System.nanoTime()
        return try {
            val response = runBlocking {
                client.request(url) {
                    method = if (ranged) HttpMethod.Get else HttpMethod("PROPFIND")
                    header(HttpHeaders.Authorization, auth)
                    if (ranged) {
                        header(HttpHeaders.Range, "bytes=0-0")
                    } else {
                        header("Depth", depth ?: "1")
                        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:getetag/><d:getcontentlength/></d:prop></d:propfind>"""
                        setBody(TextContent(body, ContentType.Application.Xml))
                    }
                }
            }
            val code = response.status.value
            ProbeStep(
                name, code, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                response.headers[HttpHeaders.ETag] != null,
                response.headers[HttpHeaders.AcceptRanges]?.equals("bytes", ignoreCase = true) == true,
                if (ranged) {
                    if (code == 206) "Частичный ответ подтверждён" else "Range не подтверждён"
                } else if (code == 207) "Multi-Status получен" else "Проверить HTTP-код",
            )
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            // Exceptions may contain request URLs or proxy credentials: never export messages.
            ProbeStep(name, null, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                false, false, "Сетевая ошибка или TLS")
        }
    }
}
