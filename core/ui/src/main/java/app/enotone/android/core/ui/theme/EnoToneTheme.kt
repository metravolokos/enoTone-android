@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package app.enotone.android.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enotone.android.core.ui.R

/** Источник всех цветов — скопированные без правки XML enotone-design 907a01c. */
data class EnoColorTokens(
    val accentFill: Color,
    val accentText: Color,
    val onAccent: Color,
    val surface2: Color,
    val text: Color,
    val textSecondary: Color,
    val line: Color,
    val shadow: Color,
    val scrim: Color,
    val coverOutline: Color,
    val playingOverlay: Color,
    val playingBar: Color,
    val avatars: List<Pair<Color, Color>>,
)

private val LocalEnoColors = staticCompositionLocalOf<EnoColorTokens> {
    error("EnoToneTheme must wrap design components")
}

/** Дополнительные роли Android v2 вне Material 3 ColorScheme. */
object EnoColors {
    val accentFill: Color @Composable get() = LocalEnoColors.current.accentFill
    val accentText: Color @Composable get() = LocalEnoColors.current.accentText
    val onAccent: Color @Composable get() = LocalEnoColors.current.onAccent
    val surface2: Color @Composable get() = LocalEnoColors.current.surface2
    val text: Color @Composable get() = LocalEnoColors.current.text
    val textSecondary: Color @Composable get() = LocalEnoColors.current.textSecondary
    val line: Color @Composable get() = LocalEnoColors.current.line
    val shadow: Color @Composable get() = LocalEnoColors.current.shadow
    val scrim: Color @Composable get() = LocalEnoColors.current.scrim
    val coverOutline: Color @Composable get() = LocalEnoColors.current.coverOutline
    val playingOverlay: Color @Composable get() = LocalEnoColors.current.playingOverlay
    val playingBar: Color @Composable get() = LocalEnoColors.current.playingBar
    val avatars: List<Pair<Color, Color>> @Composable get() = LocalEnoColors.current.avatars
}

/** 56dp — радиус устройства, намеренно отсутствует в токенах. */
object EnoRadii {
    val xs = 10.dp
    val s = 16.dp
    val m = 22.dp
    val l = 24.dp
    val xl = 28.dp
    val sheet = 48.dp
    val full = RoundedCornerShape(percent = 50)
}

data class EnoShadow(val offsetY: Dp, val blur: Dp)

/** Смещение и размер размытия теней из Android v2; цвет из EnoColors.shadow. */
object EnoElevation {
    val nowPlaying = EnoShadow(offsetY = 22.dp, blur = 44.dp)
    val albumCover = EnoShadow(offsetY = 18.dp, blur = 40.dp)
    val floatingPlayer = EnoShadow(offsetY = 8.dp, blur = 24.dp)
    val bottomBar = EnoShadow(offsetY = 8.dp, blur = 24.dp)
    val actionSheet = EnoShadow(offsetY = (-10).dp, blur = 40.dp)
    val coverBorder = 1.dp
}

private val onest = FontFamily(
    Font(
        R.font.onest,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.onest,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.onest,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)
private val unbounded = FontFamily(
    Font(
        R.font.unbounded,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

/** Отсутствующие в макете стили M3 наследуют Onest, а не системный Roboto. */
private val enoTypography = Typography(
    displayLarge = TextStyle(fontFamily = onest, fontWeight = FontWeight.SemiBold, fontSize = 36.sp),
    displayMedium = TextStyle(fontFamily = onest, fontWeight = FontWeight.SemiBold, fontSize = 32.sp),
    displaySmall = TextStyle(fontFamily = onest, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineLarge = TextStyle(fontFamily = unbounded, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 33.sp),
    headlineMedium = TextStyle(fontFamily = onest, fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
    headlineSmall = TextStyle(fontFamily = unbounded, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.8.sp),
    titleLarge = TextStyle(fontFamily = onest, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 28.75.sp),
    titleMedium = TextStyle(fontFamily = onest, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleSmall = TextStyle(fontFamily = onest, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = onest, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = onest, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = onest, fontWeight = FontWeight.Normal, fontSize = 13.5.sp),
    labelLarge = TextStyle(fontFamily = onest, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp),
    labelMedium = TextStyle(fontFamily = onest, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = onest, fontWeight = FontWeight.Medium, fontSize = 11.5.sp),
)

private val enoShapes = Shapes(
    extraSmall = RoundedCornerShape(EnoRadii.xs),
    small = RoundedCornerShape(EnoRadii.s),
    medium = RoundedCornerShape(EnoRadii.m),
    large = RoundedCornerShape(EnoRadii.xl),
    extraLarge = RoundedCornerShape(EnoRadii.sheet),
)

@Composable
private fun colorTokens(): EnoColorTokens = EnoColorTokens(
    accentFill = colorResource(R.color.eno_accent),
    accentText = colorResource(R.color.eno_accent_text),
    onAccent = colorResource(R.color.eno_on_accent),
    surface2 = colorResource(R.color.eno_surface2),
    text = colorResource(R.color.eno_text),
    textSecondary = colorResource(R.color.eno_text_secondary),
    line = colorResource(R.color.eno_line),
    shadow = colorResource(R.color.eno_shadow),
    scrim = colorResource(R.color.eno_scrim),
    coverOutline = colorResource(R.color.eno_cover_outline),
    playingOverlay = colorResource(R.color.eno_playing_overlay),
    playingBar = colorResource(R.color.eno_playing_bar),
    avatars = listOf(
        colorResource(R.color.eno_avatar_1) to colorResource(R.color.eno_avatar_on_1),
        colorResource(R.color.eno_avatar_2) to colorResource(R.color.eno_avatar_on_2),
        colorResource(R.color.eno_avatar_3) to colorResource(R.color.eno_avatar_on_3),
        colorResource(R.color.eno_avatar_4) to colorResource(R.color.eno_avatar_on_4),
        colorResource(R.color.eno_avatar_5) to colorResource(R.color.eno_avatar_on_5),
        colorResource(R.color.eno_avatar_6) to colorResource(R.color.eno_avatar_on_6),
    ),
)

/** Режим System/Dark/Light остаётся в :app — эта тема получает уже вычисленный флаг. */
@Composable
fun EnoToneTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    // User-selected DARK/LIGHT must override *resource* night qualifiers, not just M3 scheme.
    // Otherwise a dark manual mode under a light phone would load values/eno_colors.xml.
    val themedConfiguration = remember(baseContext, darkTheme) {
        Configuration(baseContext.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
    }
    val themedContext = remember(baseContext, themedConfiguration) {
        baseContext.createConfigurationContext(themedConfiguration)
    }
    CompositionLocalProvider(
        LocalContext provides themedContext,
        LocalConfiguration provides themedConfiguration,
    ) {
        EnoToneThemeContents(darkTheme) {
            // The resource configuration is local to palette extraction. UI children
            // still need the real Activity context (ActivityResultRegistry, intents, etc.).
            CompositionLocalProvider(
                LocalContext provides baseContext,
                LocalConfiguration provides baseConfiguration,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun EnoToneThemeContents(darkTheme: Boolean, content: @Composable () -> Unit) {
    val background = colorResource(R.color.eno_background)
    val surface = colorResource(R.color.eno_surface)
    val surface2 = colorResource(R.color.eno_surface2)
    val text = colorResource(R.color.eno_text)
    val secondaryText = colorResource(R.color.eno_text_secondary)
    val line = colorResource(R.color.eno_line)
    val accent = colorResource(R.color.eno_accent)
    val accentText = colorResource(R.color.eno_accent_text)
    val onAccent = colorResource(R.color.eno_on_accent)
    val scrim = colorResource(R.color.eno_scrim)
    val primary = if (darkTheme) accent else accentText
    val onPrimary = if (darkTheme) onAccent else surface
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = accent, onPrimaryContainer = onAccent,
            secondary = primary, onSecondary = onPrimary,
            secondaryContainer = accent, onSecondaryContainer = onAccent,
            tertiary = primary, onTertiary = onPrimary,
            background = background, onBackground = text,
            surface = surface, onSurface = text,
            surfaceVariant = surface2, onSurfaceVariant = secondaryText,
            surfaceContainerLowest = surface, surfaceContainerLow = surface,
            surfaceContainer = surface, surfaceContainerHigh = surface2,
            surfaceContainerHighest = surface2,
            outline = secondaryText, outlineVariant = line,
            scrim = scrim, inverseSurface = text, inverseOnSurface = background,
            inversePrimary = accentText, surfaceTint = Color.Transparent,
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = accent, onPrimaryContainer = onAccent,
            secondary = primary, onSecondary = onPrimary,
            secondaryContainer = accent, onSecondaryContainer = onAccent,
            tertiary = primary, onTertiary = onPrimary,
            background = background, onBackground = text,
            surface = surface, onSurface = text,
            surfaceVariant = surface2, onSurfaceVariant = secondaryText,
            surfaceContainerLowest = surface, surfaceContainerLow = surface,
            surfaceContainer = surface, surfaceContainerHigh = surface2,
            surfaceContainerHighest = surface2,
            outline = secondaryText, outlineVariant = line,
            scrim = scrim, inverseSurface = text, inverseOnSurface = background,
            inversePrimary = accentText, surfaceTint = Color.Transparent,
        )
    }
    CompositionLocalProvider(LocalEnoColors provides colorTokens()) {
        MaterialTheme(colorScheme = scheme, typography = enoTypography, shapes = enoShapes, content = content)
    }
}
