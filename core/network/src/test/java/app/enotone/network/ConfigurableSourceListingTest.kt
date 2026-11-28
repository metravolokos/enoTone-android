package app.enotone.network

import app.enotone.android.SourceEndpoint
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigurableSourceListingTest {
    @Test fun listsAUserChosenFolderAndNeverLeaksItsPath() {
        val server = MockWebServer()
        server.start()
        try {
            val xml = """<?xml version="1.0"?>
                <d:multistatus xmlns:d="DAV:">
                  <d:response><d:href>/MP3/Collection/</d:href><d:propstat><d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                  <d:response><d:href>/MP3/Collection/song.flac</d:href><d:propstat><d:prop>
                    <d:resourcetype/><d:getcontentlength>10</d:getcontentlength>
                  </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                </d:multistatus>""".trimIndent()
            server.enqueue(MockResponse.Builder().code(207).body(xml).build())
            val endpoint = SourceEndpoint.ofUrl(server.url("/MP3/Collection/").toString(), true)
            FlatLibraryWebDavClient(endpoint).use { client ->
                val listing = runBlocking { client.list("user@example.test", "secret") }
                assertEquals(1, listing.tracks.size)
                assertEquals("/MP3/Collection/song.flac", listing.tracks.single().encodedPath)
            }
            assertEquals("/MP3/Collection/", server.takeRequest().url.encodedPath)
        } finally {
            server.close()
        }
    }

    @Test fun diagnosticsUsesTheConfiguredFolderAndAHiddenLabel() {
        val server = MockWebServer()
        server.start()
        try {
            repeat(2) { server.enqueue(MockResponse.Builder().code(207).build()) }
            val endpoint = SourceEndpoint.ofUrl(server.url("/Music/").toString(), true)
            val report = WebDavDiagnostics(endpoint).inspect("user@example.test", "secret")
            assertEquals("PROPFIND /", report.steps[0].operation)
            assertEquals("PROPFIND folder", report.steps[1].operation)
            assertEquals("/", server.takeRequest().url.encodedPath)
            assertEquals("/Music", server.takeRequest().url.encodedPath)
        } finally {
            server.close()
        }
    }

    @Test fun defaultFolderKeepsTheHistoricDiagnosticsLabel() {
        val server = MockWebServer()
        server.start()
        try {
            repeat(2) { server.enqueue(MockResponse.Builder().code(207).build()) }
            val endpoint = SourceEndpoint.ofTestOrigin(server.url("/").toString(), true)
            val report = WebDavDiagnostics(endpoint).inspect("user@example.test", "secret")
            assertEquals("PROPFIND /AllMusic", report.steps[1].operation)
            assertEquals("/", server.takeRequest().url.encodedPath)
            assertEquals("/AllMusic", server.takeRequest().url.encodedPath)
        } finally {
            server.close()
        }
    }

    @Test fun rejectsCleartextCustomFolderOutsideExplicitLoopback() {
        try {
            FlatLibraryWebDavClient(SourceEndpoint("http://evil.example", "/Music")).close()
            throw AssertionError("Expected HTTPS restriction")
        } catch (_: IllegalArgumentException) {}
    }
}
