package app.enotone.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Separate user controls, not mixed with technical tag indexing tools. */
@Composable
internal fun ArtworkSettingsPanel(
    mode: ArtworkLoadMode,
    onModeChange: (ArtworkLoadMode) -> Unit,
    status: ArtworkScanStatus,
    storedBytes: Long?,
    clearBusy: Boolean,
    clearFeedback: String,
    onClear: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.artwork_settings_title),
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.artwork_settings_info),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        for ((value, title) in listOf(
            ArtworkLoadMode.AUTO_AFTER_IMPORT to R.string.artwork_mode_auto,
            ArtworkLoadMode.ON_DEMAND to R.string.artwork_mode_visible,
        )) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    onModeChange(value)
                }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = mode == value, onClick = null)
                Text(stringResource(title),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
            }
        }
        if (status.total > 0) {
            Text(stringResource(R.string.artwork_index_progress,
                status.completed, status.total),
                style = MaterialTheme.typography.bodySmall)
        }
        if (status.state == ArtworkScanState.RUNNING) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPause) {
                    Text(stringResource(R.string.managed_index_pause))
                }
                OutlinedButton(onClick = onStop) {
                    Text(stringResource(R.string.managed_index_stop))
                }
            }
        }
        if (storedBytes != null) {
            val mib = storedBytes.toDouble() / (1024.0 * 1024.0)
            Text(stringResource(R.string.artwork_cache_size,
                String.format(java.util.Locale.getDefault(), "%.1f", mib)),
                style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            enabled = !clearBusy &&
                status.state != ArtworkScanState.RUNNING && !status.terminating,
            onClick = { confirmClear = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.artwork_cache_clear)) }
        if (status.state == ArtworkScanState.RUNNING || status.terminating) {
            Text(stringResource(R.string.artwork_cache_clear_busy),
                style = MaterialTheme.typography.bodySmall)
        }
        if (clearFeedback.isNotBlank()) {
            Text(clearFeedback, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.artwork_cache_confirm_title)) },
            text = { Text(stringResource(R.string.artwork_cache_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text(stringResource(R.string.artwork_cache_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.album_refresh_cancel))
                }
            },
        )
    }
}
