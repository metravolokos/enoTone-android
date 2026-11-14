package app.enotone.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.android.core.ui.component.EnoShuffleAllCard
import app.enotone.android.core.ui.theme.EnoColors
import java.text.NumberFormat

/**
 * «Домой», вариант C2 (D3, макет `09_home`): крупный заголовок и вход в «Настройки» справа,
 * плашка «Перемешать всё · N песен» с Бандитом. Секции «Недавно добавленные» — D4: в базе пока
 * нет даты добавления трека, поэтому их нет и заглушек вместо них тоже нет.
 * [songCount] — `null`, пока медиатека не прочитана или пуста: тогда плашки нет.
 */
@Composable
fun FeatureEnoToneHomeScreen(
    connectedAccount: Boolean,
    songCount: Int?,
    onSettings: () -> Unit,
    onShuffleAll: () -> Unit,
    ids: HomeScreenIds,
    contentPadding: PaddingValues,
    shuffleEnabled: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(contentPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 18.dp, top = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(ids.navHome),
                style = MaterialTheme.typography.headlineLarge,
                color = EnoColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSettings) {
                Icon(
                    // Контурные «ползунки» из макета 09_home (D3.1), не шестерёнка ADR-0073.
                    painter = painterResource(EnoIcons.TUNE.outline),
                    contentDescription = stringResource(ids.settingsTitle),
                    modifier = Modifier.size(26.dp),
                    tint = EnoColors.text,
                )
            }
        }
        when {
            !connectedAccount -> Text(
                text = stringResource(ids.homeConnectAccount),
                style = MaterialTheme.typography.titleMedium,
                color = EnoColors.accentText,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 18.dp)
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onSettings)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            songCount != null && songCount > 0 -> EnoShuffleAllCard(
                title = stringResource(ids.homeShuffleAll),
                subtitle = pluralStringResource(
                    ids.homeSongCount, songCount, NumberFormat.getIntegerInstance().format(songCount),
                ),
                onClick = onShuffleAll,
                enabled = shuffleEnabled,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
            )
        }
    }
}
