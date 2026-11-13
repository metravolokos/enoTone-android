package app.enotone.android.core.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.theme.EnoToneTheme

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Preview(name = "Graphite", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Porcelain", uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class EnoBothThemes

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    EnoToneTheme(darkTheme = isSystemInDarkTheme()) {
        Box(Modifier.width(360.dp).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            content()
        }
    }
}

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

@EnoBothThemes
@Composable
private fun IconsPreview() = PreviewFrame {
    Row {
        EnoIcon(EnoIcons.HOME, "Домой")
        EnoIcon(EnoIcons.HOME, "Домой", selected = true)
        EnoIcon(EnoIcons.HOME, "Домой", enabled = false)
    }
}

@EnoBothThemes
@Composable
private fun BanditPreview() = PreviewFrame {
    Row {
        BanditIllustration(BanditPose.BOOKS, "Бандит", Modifier.size(96.dp))
        BanditIllustration(BanditPose.HEADPHONES, "Бандит", Modifier.size(96.dp))
    }
}

@EnoBothThemes
@Composable
private fun WordmarkPreview() = PreviewFrame { EnoWordmark("enoTone", Modifier.width(180.dp)) }

@EnoBothThemes
@Composable
private fun PillPreview() = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EnoPill("Играть", {}, accent = true, icon = EnoIcons.PLAY)
        EnoPill("Перемешать", {}, icon = EnoIcons.SHUFFLE)
    }
}

@EnoBothThemes
@Composable
private fun RoundButtonPreview() = PreviewFrame {
    Row {
        EnoRoundButton(EnoIcons.MORE, "Ещё", {})
        EnoRoundButton(EnoIcons.PLAY, "Играть", {}, accent = true, size = 48.dp)
    }
}

@EnoBothThemes
@Composable
private fun WavePreview() = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EnoPlayingIndicator(true, "Играет", Modifier.size(44.dp), animated = false)
        EnoPlayingIndicator(false, "Пауза", Modifier.size(44.dp))
        EnoPlayingIndicator(true, "Играет", Modifier.size(30.dp), PlayingIndicatorVariant.TRACK_NUMBER, animated = false)
    }
}

@EnoBothThemes
@Composable
private fun TrackPreview() = PreviewFrame {
    Column {
        EnoTrackRow("Пример песни", "Пример артиста", {}, {}, "Ещё", "Играет", isCurrent = true, isPlaying = true)
        EnoTrackRow("Пример песни", "Пример артиста", {}, {}, "Ещё", "Пауза", variant = EnoTrackRowVariant.ALBUM, trackNumber = "2")
    }
}

@EnoBothThemes
@Composable
private fun AlbumPreview() = PreviewFrame {
    EnoAlbumTile("Пример альбома", "Воображаемый артист", {}, Modifier.width(150.dp))
}

@EnoBothThemes
@Composable
private fun AvatarPreview() = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EnoLetterAvatar("Алиса", "Артист")
        EnoLetterAvatar("Bandit", "Артист")
        EnoLetterAvatar("🦝", "Артист")
        EnoLetterAvatar("", "Артист")
    }
}
