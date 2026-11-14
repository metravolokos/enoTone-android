package app.enotone.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview

/** Android system / Dark / Light. Color accent selection was deliberately removed. */
@Composable
internal fun AppearanceSettings() {
    val context = LocalContext.current
    AppearanceSettingsContent(
        selectedMode = UiAppearance.mode,
        onMode = { mode -> UiAppearance.chooseMode(context, mode) },
        translucentDock = UiEffects.translucentDock,
        onTranslucentDock = { UiEffects.chooseTranslucentDock(context, it) },
    )
}

@Composable
internal fun AppearanceSettingsContent(
    selectedMode: UiMode,
    onMode: (UiMode) -> Unit,
    translucentDock: Boolean = false,
    onTranslucentDock: (Boolean) -> Unit = {},
) {
    Column {
        Text(stringResource(R.string.appearance_title),
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.appearance_theme),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        UiMode.entries.forEach { mode ->
            val label = when (mode) {
                UiMode.SYSTEM -> R.string.appearance_system
                UiMode.DARK -> R.string.appearance_dark
                UiMode.LIGHT -> R.string.appearance_light
            }
            ListItem(
                headlineContent = { Text(stringResource(label)) },
                trailingContent = {
                    RadioButton(
                        selected = selectedMode == mode,
                        onClick = null,
                    )
                },
                modifier = Modifier.fillMaxWidth().clickable {
                    onMode(mode)
                },
                colors = androidx.compose.material3.ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
        // D3.2 (владелец): по умолчанию выключено — панели непрозрачные, как в макетах.
        ListItem(
            headlineContent = { Text(stringResource(R.string.appearance_translucent_dock)) },
            supportingContent = { Text(stringResource(R.string.appearance_translucent_dock_hint)) },
            trailingContent = { Switch(checked = translucentDock, onCheckedChange = null) },
            modifier = Modifier.fillMaxWidth().toggleable(
                value = translucentDock,
                role = Role.Switch,
                onValueChange = onTranslucentDock,
            ),
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
    }
}

@Preview
@Composable
internal fun AppearanceSettingsPreview() {
    MaterialTheme { AppearanceSettingsContent(UiMode.SYSTEM, {}) }
}
