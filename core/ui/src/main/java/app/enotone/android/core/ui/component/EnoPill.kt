package app.enotone.android.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.android.core.ui.theme.EnoRadii

/** Android v2, высота 48dp. Вся логика и строки предоставляются экраном. */
@Composable
fun EnoPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    icon: EnoIcons? = null,
    contentDescription: String = label,
    horizontalPadding: Dp = 10.dp,
) {
    val background = if (accent) EnoColors.accentFill else EnoColors.surface2
    val foreground = if (accent) EnoColors.onAccent else EnoColors.text
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp).defaultMinSize(minWidth = 48.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = EnoRadii.full,
        color = background,
        contentColor = foreground,
    ) {
        Row(
            // 10 dp: «Перемешать» без обрезки в двух пилюлях на 360–377 dp (макет 02_songs); в шапке альбома
            // рядом ещё «⋮» Ø 48 — там поля меньше (05_album).
            modifier = Modifier.padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                // Иконки из общей палитры; внешняя пилюля задаёт свой цвет текста.
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(icon.outline),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = foreground,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Видимый диаметр 40/48dp, вся зона 48dp кликабельна для TalkBack и касания. */
@Composable
fun EnoRoundButton(
    icon: EnoIcons,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    accent: Boolean = false,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    val diameter = if (size > 48.dp) 48.dp else size
    // filled = false — значок без подложки («⋮» в строках, поиск в полоске); зона нажатия та же 48 dp.
    val background = when {
        !filled -> Color.Transparent
        accent -> EnoColors.accentFill
        else -> EnoColors.surface2
    }
    val foreground = if (accent) EnoColors.onAccent else EnoColors.text
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier.size(48.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(diameter)
                    .background(background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(icon.outline),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = foreground,
                )
            }
        }
    }
}
