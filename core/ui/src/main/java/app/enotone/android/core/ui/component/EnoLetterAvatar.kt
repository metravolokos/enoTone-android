@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package app.enotone.android.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enotone.android.core.ui.R
import app.enotone.android.core.ui.theme.EnoColors
import java.text.BreakIterator
import java.util.Locale

/** Цвет — стабильная сумма Unicode codepoints, без platform-dependent hash. */
fun avatarPaletteIndex(name: String): Int {
    val sum = name.codePoints().toArray().fold(0L) { acc, codePoint ->
        acc + codePoint.toLong()
    }
    return (sum % AVATAR_PALETTE_SIZE).toInt()
}

/** Первый графемный кластер, включая emoji и составные буквы. */
fun avatarInitial(name: String, locale: Locale = Locale.ROOT): String {
    val text = name.trim()
    val end = if (text.isEmpty()) {
        BreakIterator.DONE
    } else {
        BreakIterator.getCharacterInstance(locale).apply { setText(text) }.next()
    }
    return if (end == BreakIterator.DONE || end <= 0) "?" else text.substring(0, end).uppercase(locale)
}

private val avatarUnbounded = FontFamily(
    Font(
        R.font.unbounded,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

@Composable
fun EnoLetterAvatar(
    name: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val (background, foreground) = EnoColors.avatars[avatarPaletteIndex(name)]
    Box(
        modifier = modifier.size(48.dp).background(background, CircleShape)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatarInitial(name),
            style = TextStyle(
                fontFamily = avatarUnbounded,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = foreground,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Число пар фон/буква в палитре v1.4 (`eno_avatar_1..6`). */
private const val AVATAR_PALETTE_SIZE = 6L
