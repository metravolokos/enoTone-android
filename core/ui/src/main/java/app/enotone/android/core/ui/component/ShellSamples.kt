package app.enotone.android.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.theme.EnoToneTheme

/** Каркас D3 с выдуманными данными: полоска прокрутки над списком, «Перемешать всё», мини-плеер, панель. */
@Composable
internal fun ShellGallery(darkTheme: Boolean) {
    EnoToneTheme(darkTheme) {
        val blur = rememberEnoBlurState()
        Column(
            modifier = Modifier.width(360.dp).background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                Column(Modifier.enoBlurSource(blur).padding(horizontal = 16.dp)) {
                    repeat(SAMPLE_ROWS) { index ->
                        EnoTrackRow(
                            title = "Вымышленная песня ${index + 1}",
                            subtitle = "Выдуманный артист",
                            onClick = {}, onMore = {}, moreContentDescription = "Действия",
                            playingDescription = "Играет",
                        )
                    }
                }
                EnoCollapsingStrip(visible = true, title = "Песни", blur = blur) {
                    EnoRoundButton(EnoIcons.SHUFFLE, "Перемешать", onClick = {})
                    EnoRoundButton(EnoIcons.PLAY, "Играть", onClick = {}, accent = true)
                    EnoRoundButton(EnoIcons.SEARCH, "Поиск", onClick = {}, filled = false)
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EnoShuffleAllCard(title = "Перемешать всё", subtitle = "1 234 песни", onClick = {},
                    modifier = Modifier.padding(horizontal = 8.dp))
                EnoMiniPlayer(
                    title = "Очень длинное название вымышленной песни",
                    subtitle = "Выдуманный артист",
                    isPlaying = true,
                    progress = 0.38f,
                    buffered = 0.64f,
                    labels = EnoMiniPlayerLabels("Открыть", "Играть", "Пауза", "Следующий"),
                    onOpen = {}, onPlayPause = {}, onNext = {},
                )
                EnoBottomBar(
                    items = listOf(
                        EnoBottomBarItem(EnoIcons.HOME, "Домой"),
                        EnoBottomBarItem(EnoIcons.SONGS, "Песни"),
                        EnoBottomBarItem(EnoIcons.ALBUMS, "Альбомы"),
                        EnoBottomBarItem(EnoIcons.ARTISTS, "Артисты"),
                        EnoBottomBarItem(EnoIcons.ALBUM_ARTISTS, "Авторы"),
                    ),
                    selectedIndex = 1,
                    onSelect = {},
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
    }
}

@Preview(name = "Shell Graphite", widthDp = 360)
@Composable
internal fun ShellGalleryGraphitePreview() = ShellGallery(darkTheme = true)

@Preview(name = "Shell Porcelain", widthDp = 360)
@Composable
internal fun ShellGalleryPorcelainPreview() = ShellGallery(darkTheme = false)

private const val SAMPLE_ROWS = 4
