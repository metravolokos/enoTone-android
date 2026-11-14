package app.enotone.android.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.theme.EnoShadow
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Мягкая тень как в макетах (CSS `box-shadow: 0 offsetY blur color`): в отличие от
 * `Modifier.shadow` (тень от высоты, почти не видна на тёмном фоне) цвет и размытие — ровно
 * из токенов. Рисуется под элементом и не обрезается его формой.
 */
fun Modifier.enoShadow(shadow: EnoShadow, color: Color, cornerRadius: Dp): Modifier = drawBehind {
    val radius = cornerRadius.toPx()
    drawIntoCanvas { canvas ->
        val paint = Paint()
        paint.asFrameworkPaint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.TRANSPARENT
            // У Android радиус тени ≈ половина размытия CSS.
            setShadowLayer(shadow.blur.toPx() / 2f, 0f, shadow.offsetY.toPx(), color.toArgb())
        }
        canvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint)
    }
}

/**
 * Полупрозрачный фон с размытием того, что под элементом (Haze, ADR-0086). Источник —
 * содержимое, помеченное [enoBlurSource] с тем же [state].
 */
fun Modifier.enoFrosted(
    state: EnoBlurState,
    background: Color,
    tintAlpha: Float,
    blurRadius: Dp,
): Modifier = hazeEffect(
    state = state.haze,
    style = HazeStyle(
        backgroundColor = background,
        tint = HazeTint(background.copy(alpha = tintAlpha)),
        blurRadius = blurRadius,
        noiseFactor = 0f,
    ),
)

/**
 * Состояние размытия для мини-плеера и нижней панели. `null` — панели непрозрачные (по умолчанию);
 * не `null` — включена настройка «Прозрачные панели» (D3.2), размывается содержимое с этим
 * состоянием. Значение задаёт `:app` из настроек; компоненты настройки не читают.
 */
val LocalEnoDockBlur = staticCompositionLocalOf<EnoBlurState?> { null }

/** Непрозрачность фона дока при включённых «Прозрачных панелях». */
const val DOCK_TINT_ALPHA = 0.72f

/**
 * Фон мини-плеера и нижней панели: сплошной цвет или, при включённых «Прозрачных панелях»,
 * тот же цвет на 72 % с размытием содержимого 24 dp.
 */
internal fun Modifier.dockBackground(color: Color): Modifier = composed {
    val blur = LocalEnoDockBlur.current
    if (blur == null) background(color) else enoFrosted(blur, color, DOCK_TINT_ALPHA, DOCK_BLUR)
}

private val DOCK_BLUR = 24.dp
