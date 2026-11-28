package app.enotone.playback

import app.enotone.android.SourceEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigurablePlaybackTargetTest {
    private val custom = SourceEndpoint("https://webdav.cloud.mail.ru", "/MP3/Collection")

    @Test fun buildsUrlsForAUserChosenFolder() {
        assertEquals(
            "https://webdav.cloud.mail.ru/MP3/Collection/song%20one.mp3",
            WebDavPlaybackTarget.url("/MP3/Collection/song%20one.mp3", custom),
        )
    }

    @Test fun rejectsFilesOutsideTheConfiguredFolderAndNestedPaths() {
        val invalid = listOf(
            "/AllMusic/song.mp3",
            "/MP3/Collection/sub/song.mp3",
            "/MP3/Collection/sub%2Fsong.mp3",
            "/MP3/Collection",
            "/Other/song.mp3",
        )
        for (path in invalid) {
            assertThrows(IllegalArgumentException::class.java) {
                WebDavPlaybackTarget.url(path, custom)
            }
        }
    }
}
