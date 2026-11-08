package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class WebDavPlaybackRangeTest {
    @Test fun acceptsOnlyPreciselyMatchingRfcAndCloudMailruRanges() {
        assertEquals(65536L,
            WebDavPlaybackRange.count("bytes 0-65535/100000", 0, 65535, 100000))
        assertEquals(34464L,
            WebDavPlaybackRange.count("bytes=65536-99999/100000", 65536, 99999, 100000))
        assertEquals(1024L,
            WebDavPlaybackRange.count("bytes 0-1023/*", 0, 1023, 100000))
    }

    @Test fun neverAcceptsWrongOffsetsLengthsOrStaleVersions() {
        val invalid = listOf(
            "bytes=1-65535/100000",
            "bytes=0-65534/100000",
            "bytes=0-65535/99999",
            "bytes=0-65535/65535",
            "bytes=0-99999/100000",
            "bytes=0-65535/100000 extra",
            "bytes 0-65535",
            "garbage",
        )
        for (header in invalid) {
            assertThrows(IOException::class.java) {
                WebDavPlaybackRange.count(header, 0, 65535, 100000)
            }
        }
        assertThrows(IOException::class.java) {
            WebDavPlaybackRange.count(null, 0, 65535, 100000)
        }
        assertThrows(IOException::class.java) {
            WebDavPlaybackRange.count("bytes 0-65535/*", 0, 65535, 100)
        }
    }
}
