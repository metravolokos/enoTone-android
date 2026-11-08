package app.enotone.network

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

/** A file directly under /AllMusic. Never print paths, ETags or filenames in public diagnostics. */
data class RemoteAudioFile(
    val fileName: String,
    val encodedPath: String,
    val etag: String?,
    val sizeBytes: Long?,
    val lastModified: String?,
)

/** Counts are safe to export; tracks and their remote paths are private. */
data class FlatWebDavListing(
    val tracks: List<RemoteAudioFile>,
    val skippedDirectories: Int,
    val skippedNested: Int,
    val skippedNonAudio: Int,
    val invalidEntries: Int,
)

/**
 * Streaming, namespace-aware DAV:multistatus parser for ONE Depth:1 PROPFIND of /AllMusic.
 * It does not perform network requests or recursively traverse child folders.
 * The caller owns the input stream and must close it. No input values in exception messages.
 */
class FlatWebDavListingParser(
    rootUrl: String = "https://webdav.cloud.mail.ru/AllMusic/",
) {
    private val root: URI = URI(rootUrl.trimEnd('/') + "/").also {
        require(it.isAbsolute && it.host != null && it.userInfo == null &&
            it.query == null && it.fragment == null && it.rawPath.endsWith("/AllMusic/")) {
            "Invalid library root URL"
        }
    }
    private val encodedPrefix = root.rawPath

    fun parse(input: InputStream): FlatWebDavListing {
        val handler = Handler()
        try {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                isValidating = false
            }
            val reader = factory.newSAXParser().xmlReader
            // Android's ExpatReader does not recognize generic JAXP security features.
            // It DOES support the LexicalHandler property: startDTD rejects any DOCTYPE.
            // Keep FSP and external entity features mandatory for other providers.
            if (reader.javaClass.name != "org.apache.harmony.xml.ExpatReader") {
                reader.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
                reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            reader.entityResolver = org.xml.sax.EntityResolver { _, _ ->
                throw SAXException("External XML entities are forbidden")
            }
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
            reader.contentHandler = handler
            reader.parse(InputSource(input))
            return handler.finish()
        } catch (_: Exception) {
            // Parser exception text can contain arbitrary user/server data: do not forward it.
            throw IllegalArgumentException("Invalid or insecure WebDAV multistatus")
        }
    }

    private inner class Handler : DefaultHandler2() {
        override fun startDTD(name: String?, publicId: String?, systemId: String?) {
            throw SAXException("DOCTYPE forbidden")
        }

        private var response: MutableResponse? = null
        private var propstat: MutablePropstat? = null
        private var capture: String? = null
        private val content = StringBuilder()
        private val result = LinkedHashMap<String, RemoteAudioFile>()
        private var rootSeen = false
        private var directories = 0
        private var nested = 0
        private var other = 0
        private var invalid = 0

        override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
            if (uri != DAV) return
            val name = localName ?: return
            if (name == "multistatus") rootSeen = true
            if (name == "response") response = MutableResponse()
            if (name == "propstat" && response != null) propstat = MutablePropstat()
            if (name == "collection" && propstat != null) propstat?.collection = true
            if ((name == "href" && response != null && propstat == null) ||
                (name in PROPERTY_FIELDS && propstat != null)) {
                capture = name
                content.setLength(0)
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capture == null) return
            if (content.length + length > MAX_FIELD_CHARS) throw SAXException("Oversized DAV field")
            content.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (uri != DAV) return
            val name = localName ?: return
            if (name == capture) {
                val value = content.toString().trim()
                if (name == "href") response?.href = value
                if (name == "status") propstat?.status = value
                if (name == "getetag") propstat?.etag = value.takeIf { it.isNotEmpty() }
                if (name == "getlastmodified") propstat?.lastModified = value.takeIf { it.isNotEmpty() }
                if (name == "getcontentlength") propstat?.size = value.toLongOrNull()?.takeIf { it >= 0 }
                capture = null
            }
            if (name == "propstat") {
                propstat?.let { ps ->
                    if (ps.status?.let { STATUS_2XX.containsMatchIn(it) } == true) {
                        response?.apply {
                            validProps = true
                            collection = collection || ps.collection
                            etag = ps.etag ?: etag
                            size = ps.size ?: size
                            lastModified = ps.lastModified ?: lastModified
                        }
                    }
                }
                propstat = null
            }
            if (name == "response") {
                response?.let { consume(it) }
                response = null
                if (result.size > MAX_TRACKS) throw SAXException("Too many DAV entries")
            }
        }

        private fun consume(item: MutableResponse) {
            if (!item.validProps || item.href.isNullOrBlank()) {
                invalid++
                return
            }
            val uri = try { root.resolve(URI(item.href!!)) } catch (_: Exception) {
                invalid++
                return
            }
            if (!uri.scheme.equals(root.scheme, ignoreCase = true) ||
                !uri.host.equals(root.host, ignoreCase = true) ||
                effectivePort(uri) != effectivePort(root) || uri.userInfo != null ||
                uri.query != null || uri.fragment != null) {
                invalid++
                return
            }
            val path = uri.rawPath ?: run { invalid++; return }
            if (path.trimEnd('/') == encodedPrefix.trimEnd('/')) return // collection itself
            if (!path.startsWith(encodedPrefix)) {
                invalid++ // out of library, never request or expose
                return
            }
            val segment = path.removePrefix(encodedPrefix)
            if (segment.contains('/') || segment.endsWith("%2F", ignoreCase = true)) {
                nested++
                return
            }
            if (item.collection) {
                directories++
                return
            }
            if (segment.isEmpty() || segment.contains("%2f", ignoreCase = true) ||
                segment.contains("%5c", ignoreCase = true)) {
                invalid++
                return
            }
            val decoded = try { URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8") }
                catch (_: Exception) { invalid++; return }
            val filename = Normalizer.normalize(decoded, Normalizer.Form.NFC)
            if (filename in setOf(".", "..") || filename.any { it == '/' || it == '\\' || it.code < 32 }) {
                invalid++
                return
            }
            val extension = filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (extension !in EXTENSIONS) {
                other++
                return
            }
            if (!result.containsKey(path) && result.size == MAX_TRACKS) throw SAXException("Too many tracks")
            result[path] = RemoteAudioFile(filename, path, item.etag, item.size, item.lastModified)
        }

        fun finish(): FlatWebDavListing {
            if (!rootSeen) throw SAXException("Missing DAV multistatus")
            return FlatWebDavListing(result.values.toList(), directories, nested, other, invalid)
        }
    }

    private fun effectivePort(uri: URI) = if (uri.port != -1) uri.port
        else if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80

    private class MutableResponse(
        var href: String? = null,
        var validProps: Boolean = false,
        var collection: Boolean = false,
        var etag: String? = null,
        var size: Long? = null,
        var lastModified: String? = null,
    )
    private class MutablePropstat(
        var status: String? = null,
        var collection: Boolean = false,
        var etag: String? = null,
        var size: Long? = null,
        var lastModified: String? = null,
    )

    private companion object {
        const val DAV = "DAV:"
        const val MAX_FIELD_CHARS = 8192
        const val MAX_TRACKS = 100_000
        val PROPERTY_FIELDS = setOf("status", "getetag", "getcontentlength", "getlastmodified")
        val STATUS_2XX = Regex("""HTTP/\S+\s+2\d\d\b""", RegexOption.IGNORE_CASE)
        val EXTENSIONS = setOf("mp3", "flac", "ogg", "opus", "m4a", "aac", "wav")
    }
}
