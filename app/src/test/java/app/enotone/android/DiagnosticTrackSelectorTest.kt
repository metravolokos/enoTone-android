package app.enotone.android

import app.enotone.database.RemoteTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnosticTrackSelectorTest {
    private fun row(id: String, name: String, missing: Int = 0): RemoteTrackEntity = RemoteTrackEntity(
        id, "source", "/AllMusic/$name", name, null, 100L, null, lastSeenEpoch = 1, missingScans = missing,
    )

    @Test fun `empty selection picks one stable mp3 first and excludes missing tracks`() {
        val rows = listOf(row("0", "a.flac"), row("2", "b.mp3"), row("1", "c.mp3"), row("-1", "d.mp3", 1))
        assertEquals("1", DiagnosticTrackSelector.select(rows, "", fast = false)?.id)
        assertEquals("1", DiagnosticTrackSelector.select(rows, "", fast = true)?.id)
    }

    @Test fun `explicit path must match one flat track and normalizes unicode`() {
        val track = row("one", "é.flac")
        assertEquals("one", DiagnosticTrackSelector.select(listOf(track), "/AllMusic/e\u0301.flac", fast = true)?.id)
        assertNull(DiagnosticTrackSelector.select(listOf(track), "/AllMusic/nested/é.flac", fast = true))
        assertNull(DiagnosticTrackSelector.select(listOf(track), "/Other/é.flac", fast = true))
        assertNull(DiagnosticTrackSelector.select(listOf(track, track), "/AllMusic/é.flac", fast = true))
    }

    @Test fun `fast probe excludes unsupported format without selecting another track`() {
        val track = row("one", "a.wav")
        assertNull(DiagnosticTrackSelector.select(listOf(track), "", fast = true))
        assertEquals("one", DiagnosticTrackSelector.select(listOf(track), "", fast = false)?.id)
    }
}
