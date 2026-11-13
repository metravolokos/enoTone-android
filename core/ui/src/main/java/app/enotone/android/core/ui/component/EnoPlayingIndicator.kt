package app.enotone.android.core.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.theme.EnoColors

/** Индикатор играет поверх обложки или вместо номера трека в альбоме. */
enum class PlayingIndicatorVariant { ARTWORK, TRACK_NUMBER }

private val wavePeriodsMs = listOf(1600, 2600, 1900, 2200)
private val waveEasing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/**
 * Без собственного текста: описание «играет»/«пауза» обязан задать вызывающий.
 * При анимациях, отключённых системой, передайте animated=false; Preview статичен.
 */
@Composable
fun EnoPlayingIndicator(
    playing: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    variant: PlayingIndicatorVariant = PlayingIndicatorVariant.ARTWORK,
    animated: Boolean = true,
) {
    val isArtwork = variant == PlayingIndicatorVariant.ARTWORK
    val animate = animated && playing && !LocalInspectionMode.current
    val barColor = if (isArtwork) EnoColors.playingBar else EnoColors.accentText
    val bg = EnoColors.playingOverlay
    Box(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        if (isArtwork) {
            Box(
                Modifier.fillMaxSize().background(bg),
            )
        }
        Row(
            modifier = Modifier.align(if (isArtwork) Alignment.BottomCenter else Alignment.Center)
                .then(if (isArtwork) Modifier.padding(bottom = 12.dp) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            wavePeriodsMs.forEachIndexed { index, period ->
                val transition = rememberInfiniteTransition(label = "eno-wave-$index")
                val scale by transition.animateFloat(
                    initialValue = 0.30f,
                    targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = period, easing = waveEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "bar-$index",
                )
                Box(
                    Modifier.width(2.5.dp).height(14.dp)
                        .graphicsLayer {
                            scaleY = if (!playing) 0.25f else if (animate) scale else 0.62f
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .background(barColor, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}
