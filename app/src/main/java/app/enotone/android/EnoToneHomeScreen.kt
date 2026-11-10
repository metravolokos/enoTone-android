package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.enotone.library.LibraryPage

/** Minimal home route. Existing Cloud.Mail.ru connection and maintenance live in Settings. */
@Composable
internal fun EnoToneHomeScreen(
    visiblePages: List<LibraryPage>,
    connectedAccount: Boolean,
    onSettings: () -> Unit,
    onOpenPage: (LibraryPage) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_settings),
                    contentDescription = stringResource(R.string.settings_title),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.home_music_title),
                style = MaterialTheme.typography.headlineMedium)
            Button(
                onClick = {
                    if (connectedAccount) onOpenPage(visiblePages.first())
                    else onSettings()
                },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(stringResource(if (connectedAccount) R.string.home_open_library
                    else R.string.home_connect_account))
            }
        }
        CompactLibraryNavigation(
            selectedPage = null,
            visiblePages = visiblePages,
            onHome = {},
            onPageSelected = onOpenPage,
            // Without a connected account categories are visible but Settings
            // remains the clear entry point to configure the first connection.
            enabled = connectedAccount,
        )
    }
}
