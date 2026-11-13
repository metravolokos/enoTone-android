package app.enotone.android.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.theme.EnoToneTheme

@Composable
internal fun ComponentGallery(darkTheme: Boolean) {
    EnoToneTheme(darkTheme) {
        Column(
            modifier = Modifier.width(360.dp)
                .background(MaterialTheme.colorScheme.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EnoIcon(EnoIcons.HOME, "Домой")
                EnoIcon(EnoIcons.SONGS, "Песни", selected = true)
                EnoIcon(EnoIcons.ALBUMS, "Альбомы", enabled = false)
                EnoLetterAvatar("Алиса", "Исполнитель")
                EnoLetterAvatar("Bandit", "Исполнитель")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EnoPill("Играть", onClick = {}, accent = true, icon = EnoIcons.PLAY)
                EnoPill("Перемешать", onClick = {}, icon = EnoIcons.SHUFFLE)
                EnoRoundButton(icon = EnoIcons.MORE, contentDescription = "Действия", onClick = {})
            }
            EnoTrackRow(
                title = "Тихие улицы и далёкие огни",
                subtitle = "Вымышленный артист",
                onClick = {}, onMore = {}, moreContentDescription = "Действия",
                playingDescription = "Играет", isCurrent = true, isPlaying = true,
                animatedIndicator = false,
            )
            EnoTrackRow(
                title = "Ещё одна композиция", subtitle = "Выдуманный артист",
                onClick = {}, onMore = {}, moreContentDescription = "Действия",
                playingDescription = "Пауза", isCurrent = true,
                variant = EnoTrackRowVariant.ALBUM, trackNumber = "7",
            )
            EnoTrackRow(
                title = "Обычная песня", subtitle = "Выдуманный артист",
                onClick = {}, onMore = {}, moreContentDescription = "Действия",
                playingDescription = "Играет", variant = EnoTrackRowVariant.ALBUM, trackNumber = "8",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EnoAlbumTile(
                    title = "Маленькая история",
                    artist = "Воображаемый оркестр",
                    onClick = {},
                    modifier = Modifier.width(150.dp),
                )
                EnoAlbumTile(
                    title = "Очень длинное название вымышленного альбома",
                    artist = "Нарисованный дуэт",
                    onClick = {},
                    modifier = Modifier.width(150.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BanditIllustration(BanditPose.HEADPHONES, null, Modifier.size(64.dp))
                BanditIllustration(BanditPose.BOOKS, null, Modifier.size(64.dp))
                EnoPlayingIndicator(true, "Играет", Modifier.size(44.dp), animated = false)
                EnoPlayingIndicator(false, "Пауза", Modifier.size(44.dp))
            }
            EnoWordmark("Логотип", Modifier.width(150.dp).height(44.dp))
        }
    }
}

// Одна галерея на тему вместо превью по компоненту: тот же набор, что и в скриншот-тесте.
@Preview(name = "Graphite", widthDp = 360)
@Composable
internal fun ComponentGalleryGraphitePreview() = ComponentGallery(darkTheme = true)

@Preview(name = "Porcelain", widthDp = 360)
@Composable
internal fun ComponentGalleryPorcelainPreview() = ComponentGallery(darkTheme = false)
