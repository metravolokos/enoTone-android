package app.enotone.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Approved enoTone design tokens v1.3: "Фарфор" / "Графит".
 * Source: metravolokos/enotone-design/tokens/colors.json.
 *
 * The accent fill always uses a dark foreground for accessibility; do not
 * render pale cream text over the light pink #EBAE9F.
 * Typography/layout are transitional until the approved UI assets are added.
 */
private val Porcelain = lightColorScheme(
    primary = Color(0xFFEBAE9F),
    onPrimary = Color(0xFF211E1C),
    primaryContainer = Color(0xFFE2DCD6),
    onPrimaryContainer = Color(0xFF211E1C),
    secondary = Color(0xFFA24F3E),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFEDE9E5),
    onBackground = Color(0xFF211E1C),
    surface = Color(0xFFF8F5F1),
    onSurface = Color(0xFF211E1C),
    surfaceVariant = Color(0xFFE2DCD6),
    onSurfaceVariant = Color(0xFF5C544E),
    outline = Color(0xFF5C544E),
    outlineVariant = Color(0xFFE2DCD6),
)

private val Graphite = darkColorScheme(
    primary = Color(0xFFEBAE9F),
    onPrimary = Color(0xFF1B1A19),
    primaryContainer = Color(0xFF2F2C2A),
    onPrimaryContainer = Color(0xFFF4ECE4),
    secondary = Color(0xFF8FB0C2),
    onSecondary = Color(0xFF1B1A19),
    background = Color(0xFF1B1A19),
    onBackground = Color(0xFFF4ECE4),
    surface = Color(0xFF252321),
    onSurface = Color(0xFFF4ECE4),
    surfaceVariant = Color(0xFF2F2C2A),
    onSurfaceVariant = Color(0xFFB9AEA4),
    outline = Color(0xFFB9AEA4),
    outlineVariant = Color(0xFF2F2C2A),
)

@Composable
internal fun EnoToneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Graphite else Porcelain,
        content = content,
    )
}
