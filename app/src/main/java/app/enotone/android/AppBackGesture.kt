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
    onCancel: () -> Unit = {},
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = enabled) { events ->
            var committed = false
            try {
                events.collect { onPreview(it.progress.coerceIn(0f, 1f)) }
                committed = true
                onCommit()
            } finally {
                // После commit прежний маршрут ещё уходит из AnimatedContent.
                // Сбрасывать прогресс здесь — визуальный скачок во время exit.
                if (!committed) onCancel()
            }
        }
    } else {
        BackHandler(enabled = enabled, onBack = onCommit)
    }
}
