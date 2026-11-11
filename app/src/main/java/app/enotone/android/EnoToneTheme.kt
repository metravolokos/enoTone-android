package app.enotone.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * enoTone's approved cross-platform color tokens (design repo v1.3).
 *
 * The layout takes its content-first hierarchy from music players, not their
 * brand colors, glass effects or proprietary UI assets. No dynamic Material You
 * recoloring: the artist's album covers keep their original pixels.
 *
 * Color contrast: dark icons/text on dusty-rose filled controls; dark-rose
 * text/icons on light backgrounds. Typography masters (Onest / Unbounded)
 * must be imported from the approved asset bundle before shipping custom fonts.
 */
private object Brand {
    val rose = Color(0xFFEBAE9F)
    val graphite = Color(0xFF1B1A19)
    val darkSurface = Color(0xFF252321)
    val darkRaised = Color(0xFF2F2C2A)
    val cream = Color(0xFFF4ECE4)
    val darkSecondaryText = Color(0xFFB9AEA4)
    val blueHighlight = Color(0xFF8FB0C2)

    val porcelain = Color(0xFFEDE9E5)
    val lightSurface = Color(0xFFF8F5F1)
    val lightRaised = Color(0xFFE2DCD6)
    val ink = Color(0xFF211E1C)
    val lightSecondaryText = Color(0xFF5C544E)
    val darkRose = Color(0xFFA24F3E)
}

private val graphiteColors = darkColorScheme(
    primary = Brand.rose,
    onPrimary = Brand.ink,
    primaryContainer = Brand.rose,
    onPrimaryContainer = Brand.ink,
    secondary = Brand.blueHighlight,
    onSecondary = Brand.graphite,
    secondaryContainer = Brand.darkRaised,
    onSecondaryContainer = Brand.cream,
    background = Brand.graphite,
    onBackground = Brand.cream,
    surface = Brand.darkSurface,
    onSurface = Brand.cream,
    surfaceVariant = Brand.darkRaised,
    onSurfaceVariant = Brand.darkSecondaryText,
    outline = Brand.darkSecondaryText,
    outlineVariant = Brand.darkRaised,
)

private val porcelainColors = lightColorScheme(
    primary = Brand.darkRose,
    onPrimary = Brand.lightSurface,
    primaryContainer = Brand.rose,
    onPrimaryContainer = Brand.ink,
    secondary = Brand.lightSecondaryText,
    onSecondary = Brand.lightSurface,
    secondaryContainer = Brand.lightRaised,
    onSecondaryContainer = Brand.ink,
    background = Brand.porcelain,
    onBackground = Brand.ink,
    surface = Brand.lightSurface,
    onSurface = Brand.ink,
    surfaceVariant = Brand.lightRaised,
    onSurfaceVariant = Brand.lightSecondaryText,
    outline = Brand.lightSecondaryText,
    outlineVariant = Brand.lightRaised,
)

@Composable
internal fun EnoToneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) graphiteColors else porcelainColors,
        content = content,
    )
}
