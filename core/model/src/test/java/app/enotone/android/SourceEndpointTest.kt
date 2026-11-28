package app.enotone.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceEndpointTest {
    @Test fun defaultSourceMatchesTheHistoricFlatMailruLibrary() {
        val endpoint = SourceEndpoint.MAILRU_ALL_MUSIC
        assertEquals("https://webdav.cloud.mail.ru", endpoint.origin)
        assertEquals("/AllMusic", endpoint.rootFolder)
        assertEquals("/AllMusic/", endpoint.rootPath)
        assertEquals("https://webdav.cloud.mail.ru/AllMusic/", endpoint.rootUrl)
        assertEquals("webdav.cloud.mail.ru", endpoint.authority)
    }

    @Test fun userFolderIsPercentEncodedOnceAndKeepsNestedSegments() {
        val endpoint = SourceEndpoint.ofUserFolder("https://webdav.cloud.mail.ru", "MP3/Моя музыка")
        assertEquals("/MP3/%D0%9C%D0%BE%D1%8F%20%D0%BC%D1%83%D0%B7%D1%8B%D0%BA%D0%B0", endpoint.rootFolder)
        assertEquals("https://webdav.cloud.mail.ru/MP3/%D0%9C%D0%BE%D1%8F%20%D0%BC%D1%83%D0%B7%D1%8B%D0%BA%D0%B0/",
            endpoint.rootUrl)
    }

    @Test fun rejectsInsecureOriginRelativeFolderAndEncodedSeparators() {
        val origin = "https://webdav.cloud.mail.ru"
        assertThrows(IllegalArgumentException::class.java) {
            SourceEndpoint("http://webdav.cloud.mail.ru", "/AllMusic")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SourceEndpoint(origin, "AllMusic")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SourceEndpoint(origin, "/AllMusic/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SourceEndpoint(origin, "/a/../b")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SourceEndpoint(origin, "/a%2Fb")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SourceEndpoint("https://user:pass@webdav.cloud.mail.ru", "/AllMusic")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SourceEndpoint("$origin?x=1", "/AllMusic")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SourceEndpoint.ofUserFolder(origin, "   ")
        }
    }

    @Test fun loopbackHttpIsAllowedOnlyForExplicitTests() {
        val loopback = SourceEndpoint("http://localhost:8080", "/AllMusic", allowLoopbackHttpForTests = true)
        assertEquals("http://localhost:8080/AllMusic/", loopback.rootUrl)
        assertThrows(IllegalArgumentException::class.java) {
            SourceEndpoint("http://localhost:8080", "/AllMusic")
        }
    }
}
