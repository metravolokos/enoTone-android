package app.enotone.android

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

/** Graphite Material 3: Android theme behavior, without wallpaper dynamic color. */
internal enum class UiMode { SYSTEM, DARK, LIGHT }

/** Preserve the existing mode preference; intentionally ignore legacy accents. */
internal object UiAppearance {
    private const val PREFS = "enotone_ui_appearance_v1"
    private var initialized = false
    var mode by mutableStateOf(UiMode.SYSTEM)
        private set
    fun initialize(context: Context) {
        if (initialized) return
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = UiMode.entries.firstOrNull { it.name == prefs.getString("mode", null) }
            ?: UiMode.SYSTEM
        initialized = true
    }

    fun chooseMode(context: Context, value: UiMode) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("mode", value.name).apply()
        mode = value
    }
}

private val graphiteDark = darkColorScheme(
    primary = Color(0xFFE4E4E7),
    onPrimary = Color(0xFF202124),
    primaryContainer = Color(0xFF3A3B40),
    onPrimaryContainer = Color(0xFFF3F3F5),
    secondary = Color(0xFFC5C5CB),
    onSecondary = Color(0xFF242529),
    secondaryContainer = Color(0xFF333438),
    onSecondaryContainer = Color(0xFFE4E4E7),
    tertiary = Color(0xFFC5C5CB),
    onTertiary = Color(0xFF242529),
    background = Color(0xFF101113),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF202124),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF303136),
    onSurfaceVariant = Color(0xFFBABAC1),
    surfaceContainerLowest = Color(0xFF101113),
    surfaceContainerLow = Color(0xFF1A1B1E),
    surfaceContainer = Color(0xFF202124),
    surfaceContainerHigh = Color(0xFF303136),
    surfaceContainerHighest = Color(0xFF3A3B40),
    outline = Color(0xFF898A91),
    outlineVariant = Color(0xFF414249),
    inverseSurface = Color(0xFFF2F2F4),
    inverseOnSurface = Color(0xFF202124),
    inversePrimary = Color(0xFF38393D),
    surfaceTint = Color.Transparent,
)
private val graphiteLight = lightColorScheme(
    primary = Color(0xFF393A3E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E2E6),
    onPrimaryContainer = Color(0xFF28292D),
    secondary = Color(0xFF5F6066),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E7EA),
    onSecondaryContainer = Color(0xFF38393D),
    tertiary = Color(0xFF5F6066),
    onTertiary = Color.White,
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF202124),
    surface = Color.White,
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFE7E7EA),
    onSurfaceVariant = Color(0xFF606168),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F8),
    surfaceContainer = Color(0xFFF0F0F2),
    surfaceContainerHigh = Color(0xFFE7E7EA),
    surfaceContainerHighest = Color(0xFFDADBE0),
    outline = Color(0xFF77787D),
    outlineVariant = Color(0xFFD4D5D9),
    inverseSurface = Color(0xFF303136),
    inverseOnSurface = Color(0xFFF4F4F5),
    inversePrimary = Color(0xFFDCDDE1),
    surfaceTint = Color.Transparent,
)

@Composable
internal fun EnoToneTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    UiAppearance.initialize(context)
    val dark = when (UiAppearance.mode) {
        UiMode.SYSTEM -> isSystemInDarkTheme()
        UiMode.DARK -> true
        UiMode.LIGHT -> false
    }
    val activity = context as? Activity
    SideEffect {
        activity?.window?.let { window ->
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) graphiteDark else graphiteLight,
        typography = Typography(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) { content() }
    }
}
