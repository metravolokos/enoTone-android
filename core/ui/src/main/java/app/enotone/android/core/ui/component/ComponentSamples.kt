package app.enotone.android.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enotone.android.core.ui.theme.EnoColors
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

internal enum class EnoStateSampleKind(
    val pose: BanditPose,
    val title: String,
    val message: String,
    val topPadding: Dp,
    val actions: EnoStateSampleActions = EnoStateSampleActions(),
    val contentSlot: EnoStateContentSlot = EnoStateContentSlot.NONE,
) {
    WELCOME(
        pose = BanditPose.SITTING,
        title = "Привет! Я Бандит",
        message = "Подключите Облако Mail.ru — я найду музыку в папке AllMusic.",
        topPadding = 96.dp,
        actions = EnoStateSampleActions(primary = EnoStateAction("Подключить", {}, fillWidth = true)),
        contentSlot = EnoStateContentSlot.LOGIN,
    ),
    READING(
        pose = BanditPose.READING,
        title = "Читаю…",
        message = "1 240 из 3 590 песен",
        topPadding = 150.dp,
        actions = EnoStateSampleActions(secondary = EnoStateAction("Слушать, пока читаю", {})),
        contentSlot = EnoStateContentSlot.PROGRESS,
    ),
    NOT_FOUND(
        pose = BanditPose.THINKING,
        title = "Ничего не нашлось",
        message = "Попробуйте другое написание — я ищу по названию, исполнителю и альбому.",
        topPadding = 200.dp,
    ),
    MUSIC_MISSING(
        pose = BanditPose.BOOKS,
        title = "Где же музыка?",
        message = "В папке /AllMusic нет файлов MP3 или FLAC.",
        topPadding = 150.dp,
        actions = EnoStateSampleActions(secondary = EnoStateAction("Проверить подключение", {})),
    ),
}

internal data class EnoStateSampleActions(
    val primary: EnoStateAction? = null,
    val secondary: EnoStateAction? = null,
)

internal enum class EnoStateContentSlot { NONE, LOGIN, PROGRESS }

/** Roborazzi-эталоны и превью общих состояний в двух темах. */
@Composable
internal fun EnoStateSampleGallery(darkTheme: Boolean, sample: EnoStateSampleKind) {
    EnoToneTheme(darkTheme) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            val content: (@Composable ColumnScope.() -> Unit)? = when (sample.contentSlot) {
                EnoStateContentSlot.LOGIN -> ({ SampleLoginFields() })
                EnoStateContentSlot.PROGRESS -> ({ SampleIndexProgress() })
                EnoStateContentSlot.NONE -> null
            }
            EnoStateView(
                pose = sample.pose,
                title = sample.title,
                message = sample.message,
                primaryAction = sample.actions.primary,
                secondaryAction = sample.actions.secondary,
                modifier = Modifier.fillMaxWidth().padding(top = sample.topPadding),
                content = content,
            )
        }
    }
}

@Composable
private fun SampleLoginFields() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(
            "Почта" to "[name@mail.ru]",
            "Пароль для внешнего приложения" to "••••••••••••",
        ).forEach { (label, value) ->
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, EnoColors.line, shape)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = EnoColors.textSecondary,
                )
                Text(
                    value,
                    modifier = Modifier.padding(top = 1.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
                    color = EnoColors.text,
                )
            }
        }
    }
}

@Composable
private fun SampleIndexProgress() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = { 0.35f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = EnoColors.accentFill,
            trackColor = EnoColors.line,
        )
        Text(
            text = "/AllMusic/[Исполнитель]/[Альбом]",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = EnoColors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

internal enum class EnoErrorSheetSampleKind {
    SHORT,
    LONG,
    LONG_FONT_SCALE_2,
}

/** Roborazzi-эталоны и превью общего листа ошибки. */
@Composable
internal fun EnoErrorSheetSampleGallery(darkTheme: Boolean, sample: EnoErrorSheetSampleKind) {
    EnoToneTheme(darkTheme) {
        val baseDensity = LocalDensity.current
        val fontScale = if (sample == EnoErrorSheetSampleKind.LONG_FONT_SCALE_2) 2f else baseDensity.fontScale
        CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            EnoErrorSheet(
                message = if (sample == EnoErrorSheetSampleKind.SHORT) {
                    "Пока облако не ответило, я немного отдохну."
                } else {
                    LONG_ERROR_SAMPLE
                },
                actionLabel = "Повторить",
                onAction = {},
                onDismiss = {},
            )
        }
    }
}

private const val LONG_ERROR_SAMPLE =
    "Пока я не могу обновить медиатеку: облако слишком долго не отвечает. " +
        "Проверьте подключение к интернету и попробуйте ещё раз. Уже загруженные песни " +
        "останутся доступны, а новое подключение можно проверить позже. Если соединение " +
        "восстановится, я продолжу работу с того же места и сохраню найденную музыку. " +
        "Пока облако не отвечает, я немного отдохну."

@Preview(name = "State · Graphite", widthDp = 377, heightDp = 816)
@Composable
internal fun EnoStateSamplePreview() =
    EnoStateSampleGallery(darkTheme = true, sample = EnoStateSampleKind.WELCOME)

@Preview(name = "Error sheet · Graphite", widthDp = 377, heightDp = 816)
@Composable
internal fun EnoErrorSheetSamplePreview() =
    EnoErrorSheetSampleGallery(darkTheme = true, sample = EnoErrorSheetSampleKind.SHORT)

@Preview(name = "Error sheet · Porcelain", widthDp = 377, heightDp = 816)
@Composable
internal fun EnoErrorSheetPorcelainSamplePreview() =
    EnoErrorSheetSampleGallery(darkTheme = false, sample = EnoErrorSheetSampleKind.SHORT)
