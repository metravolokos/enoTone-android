package app.enotone.playback

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Explicit same-origin, flat /AllMusic route guard for on-demand playback.
 * Never interpolate a title, user-supplied URL or untrusted origin into a request.
 */
object WebDavPlaybackTarget {
    private const val ORIGIN = "https://webdav.cloud.mail.ru"

    fun url(encodedPath: String): String {
        require(encodedPath.startsWith("/AllMusic/")) { "Invalid playback route" }
        val file = encodedPath.removePrefix("/AllMusic/")
        require(file.isNotEmpty() && '/' !in file && '\\' !in file &&
            !file.contains("%2f", ignoreCase = true) &&
            !file.contains("%5c", ignoreCase = true)
        ) { "Only flat library entries are allowed" }

        val decoded = URLDecoder.decode(file.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        require(decoded.isNotBlank() && decoded != "." && decoded != ".." &&
            decoded.none { it == '/' || it == '\\' || it.code < 32 }
        ) { "Invalid playback file" }
        val uri = URI(ORIGIN + encodedPath)
        require(uri.scheme == "https" && uri.host == "webdav.cloud.mail.ru" &&
            uri.port == -1 && uri.userInfo == null && uri.query == null &&
            uri.rawQuery == null && uri.fragment == null &&
            uri.rawPath == encodedPath && uri.rawAuthority == "webdav.cloud.mail.ru"
        ) { "Invalid playback URI" }
        return uri.toASCIIString()
    }
}
