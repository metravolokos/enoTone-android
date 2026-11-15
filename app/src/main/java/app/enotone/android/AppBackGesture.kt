package app.enotone.android

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.collect

/**
 * На Android 14+ получает промежуточное положение системного жеста.
 * Только завершённый жест меняет навигацию; отмена возвращает экран без pop.
 */
@Composable
internal fun AppBackGesture(
    enabled: Boolean,
    onPreview: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = enabled) { events ->
            try {
                events.collect { onPreview(it.progress.coerceIn(0f, 1f)) }
                onCommit()
            } finally {
                onPreview(0f)
            }
        }
    } else {
        BackHandler(enabled = enabled, onBack = onCommit)
    }
}
