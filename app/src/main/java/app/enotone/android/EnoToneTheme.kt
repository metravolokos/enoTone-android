package app.enotone.android

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Neutral user interface with exactly one configurable chromatic accent. */
internal enum class UiMode { SYSTEM, DARK, LIGHT }
internal enum class UiAccent(val swatch: Color) {
    RED(Color(0xFFC63749)),
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

private val neutralDark = darkColorScheme(
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF242426),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF343437),
    onSurfaceVariant = Color(0xFFB9B9BF),
    outline = Color(0xFF77777B),
    outlineVariant = Color(0xFF414145),
    secondary = Color(0xFFA9A9AE),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF363639),
    onSecondaryContainer = Color.White,
    surfaceTint = Color.Transparent,
)

private val neutralLight = lightColorScheme(
    background = Color.White,
    onBackground = Color(0xFF161618),
    surface = Color(0xFFF0F0F2),
    onSurface = Color(0xFF161618),
    surfaceVariant = Color(0xFFE2E2E5),
    onSurfaceVariant = Color(0xFF606067),
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
    val accent = UiAppearance.accent.swatch
    val neutral = if (dark) neutralDark else neutralLight
    val colors = neutral.copy(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accent,
        onPrimaryContainer = Color.White,
    )
    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) { content() }
    }
}
