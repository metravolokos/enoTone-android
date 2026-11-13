package app.enotone.android.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Фиктивные подписи: screenshot-эталоны можно коммитить без данных медиатеки. */
@Composable
internal fun ThemeSpecimen(darkTheme: Boolean) {
    EnoToneTheme(darkTheme) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Песни",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Нет композиций",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Альбом — Пример",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // Цвет задаётся явно: вне Surface LocalContentColor чёрный и в «Графите» строки пропадают.
            listOf(
                "Onest 400 — Пример" to MaterialTheme.typography.bodyMedium,
                "Onest 500 — Пример" to MaterialTheme.typography.bodyLarge,
                "Onest 600 — Пример" to MaterialTheme.typography.titleSmall,
            ).forEach { (label, style) ->
                Text(label, style = style, color = MaterialTheme.colorScheme.onBackground)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorSwatch(MaterialTheme.colorScheme.surface, EnoRadii.s)
                ColorSwatch(EnoColors.surface2, EnoRadii.m)
                ColorSwatch(EnoColors.accentFill, EnoRadii.xl)
                ColorSwatch(MaterialTheme.colorScheme.primary, EnoRadii.xs)
            }
            Spacer(Modifier.height(4.dp))
            Text("Вторичный текст", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(
                Modifier.fillMaxWidth().height(1.dp).background(EnoColors.line),
            )
        }
    }
}

@Composable
private fun ColorSwatch(color: androidx.compose.ui.graphics.Color, radius: androidx.compose.ui.unit.Dp) {
    Spacer(
        Modifier.size(66.dp)
            .background(color, RoundedCornerShape(radius))
            .border(EnoElevation.coverBorder, EnoColors.coverOutline, RoundedCornerShape(radius)),
    )
}

@Preview(name = "Graphite", showBackground = true)
@Composable
internal fun GraphiteThemePreview() = ThemeSpecimen(darkTheme = true)

@Preview(name = "Porcelain", showBackground = true)
@Composable
internal fun PorcelainThemePreview() = ThemeSpecimen(darkTheme = false)
