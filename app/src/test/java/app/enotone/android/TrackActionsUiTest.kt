package app.enotone.android

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.enotone.library.LibraryPage
import app.enotone.library.OfflineTrackRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Поведенческие сценарии листа, без тестирования строк в исходниках. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w377dp-h816dp-xxxhdpi")
class TrackActionsUiTest {
    @get:Rule val rule = createComposeRule()

    private val track = OfflineTrackRow(
        id = "track-1", title = "Песня для теста",
        artist = "Вымышленный артист", album = "Придуманный альбом",
        albumArtist = "Вымышленный артист", hasStoredTagData = true,
    )

    @Test fun moreShowsBothTargetsAndAlbumOpensOverCurrentTab() {
        var route: LibraryDetail? = null
        rule.setContent {
            EnoToneTheme {
                val context = LocalContext.current
                LibraryScreenContent(
                    state = LibraryUiState(
                        loading = false, section = LibraryPage.TRACKS,
                        tracks = listOf(track), visible = listOf(track),
                    ),
                    actions = PreviewLibraryActions,
                    artworkRepository = remember(context) { ArtworkPreviewRepository(context) },
                    accountEmail = "", appPassword = "",
                    contentPadding = PaddingValues(bottom = 180.dp),
                    onOpenDetail = { route = it },
                )
            }
        }
        rule.onNodeWithContentDescription("Действия: Песня для теста").performClick()
        rule.onNodeWithText("Перейти к альбому").assertExists().performClick()
        rule.runOnIdle {
            assertEquals(LibraryDetail.Album("album:придуманный альбом\u0000вымышленный артист"), route)
        }
    }

    @Test fun longPressOpensSameSheet() {
        rule.setContent {
            EnoToneTheme {
                val context = LocalContext.current
                LibraryScreenContent(
                    state = LibraryUiState(
                        loading = false, section = LibraryPage.TRACKS,
                        tracks = listOf(track), visible = listOf(track),
                    ),
                    actions = PreviewLibraryActions,
                    artworkRepository = remember(context) { ArtworkPreviewRepository(context) },
                    accountEmail = "", appPassword = "",
                )
            }
        }
        rule.onNodeWithText("Песня для теста").performTouchInput { longClick() }
        rule.onNodeWithText("Перейти к исполнителю").assertExists()
    }
}
