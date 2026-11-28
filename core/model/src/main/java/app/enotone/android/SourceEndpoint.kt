package app.enotone.android

import java.net.URI

/**
 * The single description of where music bytes live, so no network, metadata or
 * playback layer hardcodes a host or a folder again (ADR-0106, slice A1).
 *
 * The endpoint never carries credentials, a query, a fragment, or a non-default
 * port. [rootFolder] is an already percent-encoded absolute path: it is stored
 * encoded so string building can never re-encode or double-decode a real path.
 *
 * Slice A1 exposes only [MAILRU_ALL_MUSIC] through the app; the type exists so a
 * user-chosen folder is a configuration value, not a code edit.
 */
data class SourceEndpoint(
    /** Scheme + authority only, no path or trailing slash: `https://webdav.cloud.mail.ru`. */
    val origin: String,
    /** Encoded absolute path, leading slash, no trailing slash: `/AllMusic`, `/MP3/Collection`. */
    val rootFolder: String,
    /** Loopback HTTP is permitted only for local MockWebServer tests. */
    val allowLoopbackHttpForTests: Boolean = false,
) {
    init {
        val uri = URI(origin)
        val loopback = allowLoopbackHttpForTests && uri.scheme == "http" && uri.host in LOOPBACK_HOSTS
        require((uri.scheme == "https" && uri.host != null) || loopback) { "HTTPS source required" }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.path.isNullOrEmpty() || uri.path == "/")) { "Invalid source origin" }
        require(uri.port == -1 || loopback) { "Invalid source origin port" }
        require(rootFolder.length > 1 && rootFolder.first() == '/' && rootFolder.last() != '/') {
            "Invalid source root folder"
        }
        require(rootFolder.none { it.code < SPACE_CODE || it == '?' || it == '#' }) {
            "Invalid source root folder"
        }
        val segments = rootFolder.substring(1).split('/')
        require(segments.all { it.isNotEmpty() }) { "Empty folder segment" }
        require(segments.none { it == "." || it == ".." }) { "Relative folder segment" }
        require(segments.none { it.contains("%2f", ignoreCase = true) ||
            it.contains("%5c", ignoreCase = true) }) { "Encoded path separator" }
    }

    /** Absolute request path with a trailing slash: `/AllMusic/`. */
    val rootPath: String get() = "$rootFolder/"

    /** Absolute request URL with a trailing slash: `https://webdav.cloud.mail.ru/AllMusic/`. */
    val rootUrl: String get() = "$origin$rootPath"

    /** Host authority without a scheme: `webdav.cloud.mail.ru`. */
    val authority: String get() = URI(origin).rawAuthority

    companion object {
        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")
        private const val SPACE_CODE = 32

        /** Default source: the flat `/AllMusic` folder on Cloud.Mail.ru. */
        val MAILRU_ALL_MUSIC = SourceEndpoint("https://webdav.cloud.mail.ru", "/AllMusic")

        /**
         * Build an endpoint from a user-typed folder path. Each segment is
         * percent-encoded exactly once, so an already typed value is never
         * double-encoded. The caller is responsible for [origin] validation.
         */
        fun ofUserFolder(
            origin: String,
            userFolder: String,
            allowLoopbackHttpForTests: Boolean = false,
        ): SourceEndpoint {
            val trimmed = userFolder.trim().trim('/')
            require(trimmed.isNotEmpty()) { "Empty source folder" }
            val encoded = trimmed.split('/').joinToString("/") { segment ->
                java.net.URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
            }
            return SourceEndpoint(origin, "/$encoded", allowLoopbackHttpForTests)
        }

        /** Split an absolute folder URL into origin and root folder. */
        fun ofUrl(url: String, allowLoopbackHttpForTests: Boolean = false): SourceEndpoint {
            val uri = URI(url)
            val origin = uri.scheme + "://" + uri.rawAuthority
            val path = uri.rawPath.orEmpty().trimEnd('/').ifEmpty { "/" }
            return SourceEndpoint(origin, path, allowLoopbackHttpForTests)
        }

        /** Origin-only helper: keeps the default folder for diagnostics probes. */
        fun ofTestOrigin(baseUrl: String, allowLoopbackHttpForTests: Boolean = false): SourceEndpoint {
            val uri = URI(baseUrl)
            return SourceEndpoint(
                uri.scheme + "://" + uri.rawAuthority,
                MAILRU_ALL_MUSIC.rootFolder,
                allowLoopbackHttpForTests,
            )
        }
    }
}
