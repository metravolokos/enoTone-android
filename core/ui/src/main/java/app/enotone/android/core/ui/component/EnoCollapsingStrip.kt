package app.enotone.android.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enotone.android.core.ui.theme.EnoColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Полоска прокрутки, вариант B (макеты `10_songs_scrolled`, `11_album_scrolled`): появляется,
 * когда крупный заголовок ушёл вверх. Фон — 84 % `background` поверх размытия 18 dp,
 * снизу линия `line` 70 %. Сверху — название одной строкой и [trailing], ниже — пилюли [actions].
 */
@Composable
fun EnoCollapsingStrip(
    visible: Boolean,
    title: String,
    blur: EnoBlurState,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val background = MaterialTheme.colorScheme.background
    val line = EnoColors.line.copy(alpha = LINE_ALPHA)
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .hazeEffect(
                    state = blur.haze,
                    style = HazeStyle(
                        backgroundColor = background,
                        tint = HazeTint(background.copy(alpha = TINT_ALPHA)),
                        blurRadius = 18.dp,
                        noiseFactor = 0f,
                    ),
                )
                .drawBehind {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 28.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = EnoColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                trailing()
            }
            if (actions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 2.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    content = actions,
                )
            }
        }
    }
}

private const val TINT_ALPHA = 0.84f
private const val LINE_ALPHA = 0.7f

/**
 * Состояние размытия «того, что под полоской». Haze — деталь реализации `:core:ui` (ADR-0086):
 * экраны видят только эту обёртку, поэтому смена библиотеки не затронет `:app` и `:feature:*`.
 */
@Stable
class EnoBlurState internal constructor(internal val haze: HazeState)

@Composable
fun rememberEnoBlurState(): EnoBlurState {
    val haze = rememberHazeState()
    return remember(haze) { EnoBlurState(haze) }
}

/** Помечает содержимое, которое размывается под [EnoCollapsingStrip]. */
fun Modifier.enoBlurSource(state: EnoBlurState): Modifier = hazeSource(state.haze)
