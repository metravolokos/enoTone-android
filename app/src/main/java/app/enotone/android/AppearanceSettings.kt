package app.enotone.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun AppearanceSettings() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.appearance_title),
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.appearance_theme),
            style = MaterialTheme.typography.bodyMedium)
        UiMode.values().forEach { value ->
            val label = when (value) {
                UiMode.SYSTEM -> R.string.appearance_system
                UiMode.DARK -> R.string.appearance_dark
                UiMode.LIGHT -> R.string.appearance_light
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    UiAppearance.chooseMode(context, value)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = UiAppearance.mode == value, onClick = null)
                Text(stringResource(label))
            }
        }
        Text(stringResource(R.string.appearance_accent),
            style = MaterialTheme.typography.bodyMedium)
        UiAccent.values().forEach { value ->
            val label = when (value) {
                UiAccent.RED -> R.string.appearance_red
                UiAccent.BLUE -> R.string.appearance_blue
                UiAccent.GREEN -> R.string.appearance_green
                UiAccent.ORANGE -> R.string.appearance_orange
                UiAccent.PURPLE -> R.string.appearance_purple
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    UiAppearance.chooseAccent(context, value)
                },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = UiAppearance.accent == value, onClick = null)
                Box(modifier = Modifier.size(22.dp).clip(CircleShape)
                    .background(value.swatch))
                Text(stringResource(label))
            }
        }
    }
}
