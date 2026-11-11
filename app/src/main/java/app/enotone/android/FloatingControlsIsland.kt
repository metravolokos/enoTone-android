package app.enotone.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * Apple Music Android screenshot approximation: floating translucent capsules
 * with separate mini-player and navigation footprints. Actual blur of content
 * *behind* this Compose surface needs an off-screen backdrop layer; it is not
 * approximated by blurring text or icons themselves.
 */
@Composable
internal fun FloatingControlsIsland(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(if (compact) 26.dp else 34.dp)
    val neutral = MaterialTheme.colorScheme.surface
    val border = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)
    Column(
        modifier = modifier
            .shadow(18.dp, shape, clip = false)
            .clip(shape)
            .background(neutral.copy(alpha = .81f))
            .border(.7.dp, border, shape),
        content = content,
    )
}
