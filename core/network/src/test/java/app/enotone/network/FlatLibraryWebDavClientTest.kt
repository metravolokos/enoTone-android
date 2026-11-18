package app.enotone.network

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlatLibraryWebDavClientTest {
    private val xml = """<?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:">
          <d:response><d:href>/AllMusic/</d:href><d:propstat><d:prop>
            <d:resourcetype><d:collection/></d:resourcetype>
          </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
          <d:response><d:href>/AllMusic/test.flac</d:href><d:propstat><d:prop>
            <d:resourcetype/><d:getetag>private-etag</d:getetag><d:getcontentlength>1234</d:getcontentlength>
          </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        </d:multistatus>""".trimIndent()

    @Test fun sendsOneDepth1PropfindAndParsesPrivateEntries() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse.Builder().code(207).body(xml).build())
            FlatLibraryWebDavClient(server.url("/AllMusic/").toString(), true).use { client ->
                val listing = runBlocking { client.list("user@example.test", "SECRET_password") }
                assertEquals(1, listing.tracks.size)
                assertEquals(1234L, listing.tracks.single().sizeBytes)
                assertEquals("private-etag", listing.tracks.single().etag)
            }
            assertEquals(1, server.requestCount)
            val req = server.takeRequest()
            assertEquals("PROPFIND", req.method)
            assertEquals("/AllMusic/", req.url.encodedPath)
            assertEquals("1", req.headers["Depth"])
            assertTrue(req.headers["Authorization"]!!.startsWith("Basic "))
            assertTrue(req.body!!.utf8().contains("getetag"))
        } finally {
            server.close()
        }
    }

    @Test fun rejectsRedirectInsteadOfForwardingAuthorization() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse.Builder().code(302)
                .addHeader("Location", "https://evil.example/steal").build())
            FlatLibraryWebDavClient(server.url("/AllMusic/").toString(), true).use { client ->
                try {
                    runBlocking { client.list("u", "SECRET_password") }
                    throw AssertionError("Expected HTTP error")
                } catch (e: FlatWebDavHttpError) {
                    assertEquals(302, e.statusCode)
                    assertFalse(e.message.orEmpty().contains("SECRET"))
                }
            }
            assertEquals(1, server.requestCount)
        } finally {
            server.close()
        }
    }

    @Test fun rejects401AndNeverIncludesCredentialsInError() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse.Builder().code(401).build())
            FlatLibraryWebDavClient(server.url("/AllMusic/").toString(), true).use { client ->
                try {
                    runBlocking { client.list("private@example.test", "SECRET") }
                    throw AssertionError("Expected HTTP error")
                } catch (e: FlatWebDavHttpError) {
                    assertEquals(401, e.statusCode)
                    assertFalse(e.message.orEmpty().contains("private"))
                    assertFalse(e.message.orEmpty().contains("SECRET"))
                }
            }
        } finally {
            server.close()
        }
    }

    @Test fun refusesCleartextOutsideExplicitLocalMock() {
        try {
            FlatLibraryWebDavClient("http://evil.example/AllMusic/", true).close()
            throw AssertionError("Expected HTTPS restriction")
        } catch (_: IllegalArgumentException) {}
    }
}
