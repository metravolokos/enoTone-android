package app.enotone.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import app.enotone.playback.PlayerLaunchIntent

/** Activity is only the app shell; settings UI is in SettingsScreen.kt. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        consumeOpenNowPlaying(intent)
        setContent { EnoToneTheme { DiagnosticScreen(openNowPlayingRequest = openNowPlaying.intValue) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeOpenNowPlaying(intent)
    }

    /** Каждое нажатие на уведомление — новый номер; флаг снимается, чтобы пересоздание его не повторило. */
    private val openNowPlaying = mutableIntStateOf(0)

    private fun consumeOpenNowPlaying(intent: Intent?) {
        if (intent?.getBooleanExtra(PlayerLaunchIntent.EXTRA_OPEN_NOW_PLAYING, false) != true) return
        intent.removeExtra(PlayerLaunchIntent.EXTRA_OPEN_NOW_PLAYING)
        openNowPlaying.intValue++
    }
}
