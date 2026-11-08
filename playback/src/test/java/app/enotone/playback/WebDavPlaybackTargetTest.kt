package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebDavPlaybackTargetTest {
    @Test fun permitsOneLevelEncodedAudioFilesOnTrustedHttpsOrigin() {
        assertEquals(
            "https://webdav.cloud.mail.ru/AllMusic/sample%20track.mp3",
            WebDavPlaybackTarget.url("/AllMusic/sample%20track.mp3"),
        )
        assertEquals(
            "https://webdav.cloud.mail.ru/AllMusic/%D0%A2%D0%B5%D1%81%D1%82.flac",
            WebDavPlaybackTarget.url("/AllMusic/%D0%A2%D0%B5%D1%81%D1%82.flac"),
        )
    }

    @Test fun rejectsRedirectTargetsTraversalAndEmbeddedCredentials() {
        val invalid = listOf(
            "https://evil.test/track.mp3",
            "/AllMusic/subfolder/file.flac",
            "/AllMusic/sub%2Ffile.mp3",
            "/AllMusic/%5cfile.mp3",
            "/AllMusic/%2e%2e",
            "/AllMusic/a.mp3?token=secret",
            "/AllMusic/a.mp3#fragment",
            "/AllMusic/%0A.mp3",
            "/Other/music.mp3",
            "/AllMusic/",
        )
        for (path in invalid) {
            assertThrows(IllegalArgumentException::class.java) {
                WebDavPlaybackTarget.url(path)
            }
        }
    }
}
