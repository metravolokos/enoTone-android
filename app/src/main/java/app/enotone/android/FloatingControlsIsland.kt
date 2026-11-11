package app.enotone.android

import androidx.compose.foundation.background
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
 * Translucent neutral surface above scrollable content. Do not apply
 * Modifier.blur to controls: that blurs text and buttons themselves, not the
 * content behind. A genuine backdrop-blur renderer can be added separately.
 */
@Composable
internal fun FloatingControlsIsland(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.91f)),
        content = content,
    )
}
