package app.enotone.android

import android.app.Activity
import android.content.Context
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.ui.platform.LocalContext

/** Neutral user interface with exactly one configurable chromatic accent. */
internal enum class UiMode { SYSTEM, DARK, LIGHT }
internal enum class UiAccent(val swatch: Color) {
    RED(Color(0xFFFA233B)),
    BLUE(Color(0xFF286FAE)),
    GREEN(Color(0xFF27764E)),
    ORANGE(Color(0xFFAB5926)),
    PURPLE(Color(0xFF7250A6)),
}

/** Only display options: no Room or credentials and no media-library requests. */
internal object UiAppearance {
    private const val PREFS = "enotone_ui_appearance_v1"
    private var initialized = false
    var mode by mutableStateOf(UiMode.SYSTEM)
        private set
    var accent by mutableStateOf(UiAccent.RED)
        private set
    var playerVisible by mutableStateOf(false)
        private set
    fun showNowPlaying(visible: Boolean) { playerVisible = visible }

    fun initialize(context: Context) {
        if (initialized) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = UiMode.values().firstOrNull { it.name == prefs.getString("mode", null) }
            ?: UiMode.SYSTEM
        accent = UiAccent.values().firstOrNull { it.name == prefs.getString("accent", null) }
            ?: UiAccent.RED
        initialized = true
    }

    fun chooseMode(context: Context, value: UiMode) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("mode", value.name).apply()
        mode = value
    }

    fun chooseAccent(context: Context, value: UiAccent) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("accent", value.name).apply()
        accent = value
    }
}

/** Design-space equivalents of the supplied iOS point tokens, expressed as dp/sp. */
private val musicType = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val neutralDark = darkColorScheme(
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF303034),
    onSurfaceVariant = Color(0xFF98989F),
    outline = Color(0xFF77777B),
    outlineVariant = Color(0xFF545458),
    secondary = Color(0xFFA9A9AE),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF363639),
    onSecondaryContainer = Color.White,
    surfaceTint = Color.Transparent,
)

private val neutralLight = lightColorScheme(
    background = Color.White,
    onBackground = Color(0xFF161618),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF161618),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF88888C),
    outline = Color(0xFF8C8C93),
    outlineVariant = Color(0xFFD4D4D8),
    secondary = Color(0xFF66666B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E8EA),
    onSecondaryContainer = Color(0xFF161618),
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
    // The app background must extend through both transparent system bars.
    // The icons reflect the *user-selected* theme rather than the OS default.
    val activity = context as? Activity
    SideEffect {
        activity?.window?.let { window ->
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !dark && !UiAppearance.playerVisible
            controller.isAppearanceLightNavigationBars = !dark && !UiAppearance.playerVisible
        }
    }
    val accent = if (dark) {
        if (UiAppearance.accent == UiAccent.RED) Color(0xFFFA586A)
        else lerp(UiAppearance.accent.swatch, Color.White, 0.18f)
    } else UiAppearance.accent.swatch
    val neutral = if (dark) neutralDark else neutralLight
    val colors = neutral.copy(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accent,
        onPrimaryContainer = Color.White,
    )
    MaterialTheme(colorScheme = colors, typography = musicType) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) { content() }
    }
}
