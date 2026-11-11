package app.enotone.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Single-line Apple Music-like library action. Exact touch bounds; never let
 * the long Russian label create a second line or change the layout height.
 */
@Composable
internal fun MusicListAction(
    text: String,
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(48.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(26.dp))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(19.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Compact visual seek/volume slider: 4dp rail, no Material3 M3-style thumb or
 * 40dp intrinsic track. The *touch target* remains 44dp tall. The renderer
 * never changes the Media3 timeline; the caller is the playback authority.
 */
@Composable
internal fun MusicThinSlider(
    value: Float,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit = {},
) {
    fun fraction(x: Float, width: Int): Float =
        if (width > 0) (x / width).coerceIn(0f, 1f) else 0f
    Box(
        modifier = modifier.height(44.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures { pos ->
                        onValueChange(fraction(pos.x, size.width))
                        onFinished()
                    }
                }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectDragGestures(
                        onDragStart = { pos -> onValueChange(fraction(pos.x, size.width)) },
                        onDrag = { change, _ ->
                            onValueChange(fraction(change.position.x, size.width))
                            change.consume()
                        },
                        onDragEnd = onFinished,
                    )
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(0f, 1f),
                    range = 0f..1f,
                )
                if (enabled) {
                    setProgress { target ->
                        onValueChange(target.coerceIn(0f, 1f))
                        onFinished()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.34f),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            if (value > 0f) {
                drawRoundRect(
                    color = tint.copy(alpha = 0.92f),
                    size = Size(size.width * value.coerceIn(0f, 1f), size.height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
}
