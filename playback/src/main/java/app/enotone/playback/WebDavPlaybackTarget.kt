package app.enotone.playback

import app.enotone.android.SourceEndpoint
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Explicit same-origin, flat library route guard for on-demand playback.
 * Never interpolate a title, user-supplied URL or untrusted origin into a request.
 *
 * The endpoint supplies origin and root prefix (ADR-0106, slice A1); the default
 * is the flat `/AllMusic` folder on Cloud.Mail.ru.
 */
object WebDavPlaybackTarget {
    private val defaultEndpoint = SourceEndpoint.MAILRU_ALL_MUSIC

    fun url(encodedPath: String): String = url(encodedPath, defaultEndpoint)

    @Suppress("CyclomaticComplexMethod") // Все проверки — существующие same-origin/flat-guard.
    fun url(encodedPath: String, endpoint: SourceEndpoint): String {
        val root = endpoint.rootFolder
        require(encodedPath.startsWith("$root/")) { "Invalid playback route" }
        val file = encodedPath.removePrefix("$root/")
        require(file.isNotEmpty() && '/' !in file && '\\' !in file &&
            !file.contains("%2f", ignoreCase = true) &&
            !file.contains("%5c", ignoreCase = true)
        ) { "Only flat library entries are allowed" }

        val decoded = URLDecoder.decode(file.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        require(decoded.isNotBlank() && decoded != "." && decoded != ".." &&
            decoded.none { it == '/' || it == '\\' || it.code < 32 }
        ) { "Invalid playback file" }
        val uri = URI(endpoint.origin + encodedPath)
        require(uri.scheme == (URI(endpoint.origin).scheme) && uri.host == URI(endpoint.origin).host &&
            uri.userInfo == null && uri.query == null && uri.rawQuery == null && uri.fragment == null &&
            uri.rawPath == encodedPath && uri.rawAuthority == endpoint.authority
        ) { "Invalid playback URI" }
        return uri.toASCIIString()
    }
}
