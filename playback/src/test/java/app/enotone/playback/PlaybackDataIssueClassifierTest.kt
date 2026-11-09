package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class PlaybackDataIssueClassifierTest {
    @Test fun classifiesNestedFixedCodesWithoutExceptionText() {
        val error = IOException("synthetic private URI must never be printed",
            IOException("Media3 wrapper",
                PlaybackDataIssueException(PlaybackDataIssue.OPEN_RANGE)))
        assertEquals(PlaybackDataIssue.OPEN_RANGE,
            PlaybackDataIssueClassifier.classify(error))
    }

    @Test fun unknownCauseDoesNotExposeUnderlyingMessage() {
        val error = IOException("synthetic password or path")
        assertEquals(PlaybackDataIssue.UNKNOWN, PlaybackDataIssueClassifier.classify(error))
        assertEquals("Playback data issue",
            PlaybackDataIssueException(PlaybackDataIssue.READ_TRUNCATED).message)
    }
}
