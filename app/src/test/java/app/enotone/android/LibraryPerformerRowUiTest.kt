package app.enotone.android

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.enotone.library.LibraryPage
import app.enotone.library.OfflineLibraryGrouping
import app.enotone.library.OfflineTrackRow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Поведенческая проверка списочных строк Android v2, а не наличия строк в Kotlin-исходниках. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w377dp-h816dp-xxxhdpi")
class LibraryPerformerRowUiTest {
    @get:Rule val rule = createComposeRule()

    private fun song(id: String, artist: String, album: String, albumArtist: String?) =
        OfflineTrackRow(id, id, artist, album, albumArtist, hasStoredTagData = true)

    @Test fun artistCaptionMatchesDetailPlannerAndRowOpensArtist() {
        val rows = listOf(
            song("first", "Вымышленный певец", "Первый альбом", "Вымышленный певец"),
            song("second", "Вымышленный певец", "Первый альбом", "Вымышленный певец"),
        )
        val index = LibraryGroupIndex.build(rows, "Неизвестный альбом")
        val artist = index.artists.single()
        val plan = ArtistDetailPlanner.plan(artist, index)
        var clicked = false
        rule.setContent {
            EnoToneTheme {
                LibraryPerformerRow(LibraryPage.ARTISTS, artist, plan, 0) { clicked = true }
            }
        }
        rule.onNodeWithText("1 альбом · 2 песни").assertExists()
        rule.onNodeWithText("Вымышленный певец").performClick()
        rule.runOnIdle { assertTrue(clicked) }
    }

    @Test fun compilationsOnlyArtistUsesSameCaptionAsDetails() {
        val rows = listOf(song("single", "Вымышленный певец", "Сборник песен", "VA"))
        val index = LibraryGroupIndex.build(rows, "Неизвестный альбом")
        val artist = index.artists.single()
        rule.setContent {
            EnoToneTheme {
                LibraryPerformerRow(LibraryPage.ARTISTS, artist,
                    ArtistDetailPlanner.plan(artist, index), 0) {}
            }
        }
        rule.onNodeWithText("Песни в сборниках · 1 песня").assertExists()
    }

    @Test fun albumArtistsShowCompilationGroupFirstWithCount() {
        val rows = listOf(
            song("one", "Первый", "Сборник", null),
            song("two", "Второй", "Сборник", null),
        )
        val index = LibraryGroupIndex.build(rows, "Неизвестный альбом")
        val group = index.albumArtists.single()
        assertTrue(group.key == OfflineLibraryGrouping.COMPILATIONS_KEY)
        rule.setContent {
            EnoToneTheme {
                LibraryPerformerRow(LibraryPage.ALBUM_ARTISTS, group, null,
                    index.albumArtistAlbumCounts[group.key] ?: 0) {}
            }
        }
        rule.onNodeWithText("Разные исполнители").assertExists()
        rule.onNodeWithText("1 сборник").assertExists()
    }
}
