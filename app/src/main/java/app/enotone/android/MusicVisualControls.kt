package app.enotone.android

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Real Material 3 action, native hit target and tonal response. */
@Composable
internal fun MusicListAction(
    text: String,
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null,
            modifier = Modifier.size(18.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge)
    }
}
