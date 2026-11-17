package app.enotone.android

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.enotone.library.LibraryPage
import app.enotone.library.OfflineLibraryGrouping
import app.enotone.library.OfflineTrackRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Audit a real album list while its immutable catalogue is replaced, without network. */
@RunWith(RobolectricTestRunner::class)
@Config(application = EnoToneApplication::class, sdk = [35], qualifiers = "w393dp-h1600dp")
class AlbumReindexUiAuditTest {
    @get:Rule val rule = createComposeRule()

    @Test fun metadataReplacementsKeepRowsUniqueAndHeaderCountEqual() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tracks = (1..12).map { number ->
            OfflineTrackRow(number.toString(), "Fixture $number", "Fixture", "Fixture album",
                trackNumber = number, durationMs = 1000L, hasStoredTagData = true)
        }
        val group = OfflineLibraryGrouping.albums(tracks).single()
        val state = mutableStateOf(LibraryUiState(
            section = LibraryPage.ALBUMS, selectedGroupKey = group.key,
            derivedRoute = LibraryPage.ALBUMS to group.key,
            tracks = tracks, selectedGroup = group, groups = listOf(group),
            visible = tracks, albumTracks = tracks, loading = false, currentTrackId = "1",
        ))
        rule.setContent {
            EnoToneTheme {
                LibraryScreenContent(state = state.value, actions = PreviewLibraryActions,
                    artworkRepository = ArtworkPreviewRepository(context), accountEmail = "",
                    appPassword = "", contentPadding = PaddingValues())
            }
        }
        repeat(3) { revision ->
            rule.runOnIdle {
                val updated = tracks.map { it.copy(year = 2020 + revision) }
                state.value = state.value.copy(tracks = updated, visible = updated, albumTracks = updated)
            }
            tracks.forEach { rule.onAllNodesWithText(it.title).assertCountEquals(1) }
            rule.onAllNodesWithContentDescription(context.getString(R.string.song_playing_description))
                .assertCountEquals(1)
            rule.runOnIdle {
                assertEquals(LibraryAlbumAudit(12, 12, 0), LibraryAlbumDiagnostics.last.value)
            }
        }
    }
}
