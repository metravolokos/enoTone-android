package app.enotone.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class FlatWebDavListingParserTest {
    private val parser = FlatWebDavListingParser()

    private fun parse(xml: String): FlatWebDavListing =
        parser.parse(ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)))

    private fun response(href: String, props: String, status: String = "HTTP/1.1 200 OK") =
        "<d:response><d:href>" + href + "</d:href><d:propstat><d:prop>" + props +
            "</d:prop><d:status>" + status + "</d:status></d:propstat></d:response>"

    private fun doc(body: String) =
        "<?xml version=\"1.0\"?><d:multistatus xmlns:d=\"DAV:\">" + body + "</d:multistatus>"

    @Test fun parsesOnlyDirectAudioWithPropertyStatusAndNfcNames() {
        val root = response("/AllMusic/", "<d:resourcetype><d:collection/></d:resourcetype>")
        val first = response("/AllMusic/%D0%95%CC%88%D0%B6.flac",
            "<d:getetag>etag-private</d:getetag><d:getcontentlength>12345</d:getcontentlength>")
        val second = response("/AllMusic/Mix%20%2B%20Track.MP3",
            "<d:getetag>another-private</d:getetag><d:getlastmodified>Sun, 08 Nov 2026 08:00:00 GMT</d:getlastmodified>")
        val folder = response("/AllMusic/sub/", "<d:resourcetype><d:collection/></d:resourcetype>")
        val nested = response("/AllMusic/sub/child.mp3", "<d:getcontentlength>10</d:getcontentlength>")
        val cover = response("/AllMusic/cover.jpg", "<d:getcontentlength>10</d:getcontentlength>")
        val items = parse(doc(root + first + second + folder + nested + cover))
        assertEquals(2, items.tracks.size)
        assertEquals("Ёж.flac", items.tracks[0].fileName)
        assertEquals(12345L, items.tracks[0].sizeBytes)
        assertEquals("Mix + Track.MP3", items.tracks[1].fileName)
        assertEquals(1, items.skippedDirectories)
        assertEquals(1, items.skippedNested)
        assertEquals(1, items.skippedNonAudio)
        assertEquals(0, items.invalidEntries)
    }

    @Test fun honorsPropstatFailureAndIgnoresOutsideUrls() {
        val denied = response("/AllMusic/denied.flac", "<d:getetag>no</d:getetag>", "HTTP/1.1 404 Not Found")
        val evil = response("https://evil.example/AllMusic/evil.mp3", "<d:getetag>bad</d:getetag>")
        val escaped = response("/private/escaped.mp3", "<d:getetag>bad</d:getetag>")
        val good = response("https://webdav.cloud.mail.ru/AllMusic/allowed.opus", "<d:getetag>yes</d:getetag>")
        val items = parse(doc(denied + evil + escaped + good))
        assertEquals(listOf("allowed.opus"), items.tracks.map { it.fileName })
        assertEquals(3, items.invalidEntries)
    }

    @Test fun cannotEscapeRootWithEncodedSlash() {
        val bad = response("/AllMusic/a%2Fprivate.mp3", "<d:getetag>bad</d:getetag>")
        val items = parse(doc(bad))
        assertTrue(items.tracks.isEmpty())
        assertEquals(1, items.invalidEntries)
    }

    @Test fun duplicateHrefIsListedOnce() {
        val item = response("/AllMusic/song.wav", "<d:getetag>v1</d:getetag>")
        val same = response("/AllMusic/song.wav", "<d:getetag>v2</d:getetag>")
        val items = parse(doc(item + same))
        assertEquals(1, items.tracks.size)
        assertEquals("v2", items.tracks[0].etag)
    }

    @Test fun rejectsDoctypeAndDoesNotExposeItsContents() {
        val unsafe = "<!DOCTYPE d:multistatus [ <!ENTITY secret SYSTEM \"file:///etc/passwd\"> ]>" +
            "<d:multistatus xmlns:d=\"DAV:\"><d:response><d:href>&secret;</d:href></d:response></d:multistatus>"
        var rejected = false
        try {
            parse(unsafe)
        } catch (e: IllegalArgumentException) {
            rejected = true
            assertFalse(e.message.orEmpty().contains("secret"))
            assertFalse(e.message.orEmpty().contains("/etc/"))
        }
        assertTrue(rejected)
    }

    @Test fun rejectsOversizedHrefAndMalformedXmlWithoutPathInError() {
        val bad = doc(response("/AllMusic/" + "x".repeat(9000) + ".mp3", "<d:getetag>x</d:getetag>"))
        try {
            parse(bad)
            throw AssertionError("Expected parsing failure")
        } catch (e: IllegalArgumentException) {
            assertFalse(e.message.orEmpty().contains("AllMusic/"))
        }
        try {
            parse("<not-xml")
            throw AssertionError("Expected parsing failure")
        } catch (_: IllegalArgumentException) {}
    }
}
