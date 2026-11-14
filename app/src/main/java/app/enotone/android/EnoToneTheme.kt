package app.enotone.android

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import app.enotone.android.core.ui.theme.EnoToneTheme as CoreEnoToneTheme

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

/**
 * Визуальные эффекты (D3.2, ADR-0088): «Прозрачные панели» — мини-плеер и нижняя панель с
 * размытием. Отдельный файл настроек, чтобы не трогать данные [UiAppearance]; по умолчанию выключено.
 */
internal object UiEffects {
    private const val PREFS = "enotone_ui_effects_v1"
    private const val KEY_TRANSLUCENT_DOCK = "translucent_dock"
    private var initialized = false
    var translucentDock by mutableStateOf(false)
        private set

    fun initialize(context: Context) {
        if (initialized) return
        translucentDock = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_TRANSLUCENT_DOCK, false)
        initialized = true
    }

    fun chooseTranslucentDock(context: Context, value: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_TRANSLUCENT_DOCK, value).apply()
        translucentDock = value
    }
}

@Composable
internal fun EnoToneTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    UiAppearance.initialize(context)
    UiEffects.initialize(context)
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
    CoreEnoToneTheme(darkTheme = dark) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) { content() }
    }
}
