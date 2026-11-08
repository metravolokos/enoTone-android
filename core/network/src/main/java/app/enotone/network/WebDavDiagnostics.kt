package app.enotone.network

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
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

/** M0 diagnostics only: no credential persistence, no response-body parsing, no automatic redirect. */
class WebDavDiagnostics(
    private val baseUrl: HttpUrl = "https://webdav.cloud.mail.ru/".toHttpUrl(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    fun inspect(email: String, password: String, testFile: String = "", probeInfinity: Boolean = false): ProbeReport {
        require(email.isNotBlank() && password.isNotBlank())
        val auth = Credentials.basic(email.trim(), password, Charsets.UTF_8)
        val steps = mutableListOf<ProbeStep>()
        steps += request("PROPFIND /", endpoint(emptyList()), auth, depth = "1")
        steps += request("PROPFIND /AllMusic", endpoint(listOf("AllMusic")), auth, depth = "1")

        // This call is explicitly opt-in: enumerating a large remote tree may be expensive.
        if (probeInfinity) {
            steps += request("PROPFIND Depth infinity", endpoint(listOf("AllMusic")), auth, depth = "infinity")
        } else {
            steps += skipped("PROPFIND Depth infinity", "Не запрошено")
        }

        val parts = testFile.trim().trim('/').split('/').filter { it.isNotBlank() }
        if (parts.size >= 2 && parts.first() == "AllMusic" &&
            parts.none { it == "." || it == ".." }) {
            steps += request("GET Range 0-0", endpoint(parts), auth, ranged = true)
        } else {
            steps += skipped("GET Range 0-0", "Не проверено: нужен путь /AllMusic/…")
        }
        return ProbeReport(steps)
    }

    private fun skipped(name: String, note: String): ProbeStep =
        ProbeStep(name, null, 0, false, false, note)

    // HttpUrl.addPathSegment encodes Cyrillic and other Unicode safely; host is never user-supplied in production.
    private fun endpoint(segments: List<String>): HttpUrl {
        val builder = baseUrl.newBuilder().encodedPath("/")
        segments.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun request(
        name: String, url: HttpUrl, auth: String, depth: String? = null, ranged: Boolean = false,
    ): ProbeStep {
        val builder = Request.Builder().url(url).header("Authorization", auth)
        if (ranged) {
            builder.get().header("Range", "bytes=0-0")
        } else {
            val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:getetag/><d:getcontentlength/></d:prop></d:propfind>"""
            builder.method("PROPFIND", body.toRequestBody("application/xml".toMediaType()))
                .header("Depth", depth ?: "1")
        }
        val started = System.nanoTime()
        return try {
            client.newCall(builder.build()).execute().use { response ->
                ProbeStep(
                    name, response.code, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                    response.header("ETag") != null,
                    response.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true,
                    if (ranged) {
                        if (response.code == 206) "Частичный ответ подтверждён" else "Range не подтверждён"
                    } else if (response.code == 207) "Multi-Status получен" else "Проверить HTTP-код",
                )
            }
        } catch (_: IOException) {
            // Never include exception.message: proxies or auth data could be embedded.
            ProbeStep(name, null, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                false, false, "Сетевая ошибка или TLS")
        }
    }
}
