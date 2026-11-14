package app.enotone.android.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.android.core.ui.theme.EnoElevation
import app.enotone.android.core.ui.theme.EnoRadii

/** Содержимое листа не знает о медиатеке, очереди или навигации. */
data class EnoActionSheetHeader(
    val title: String,
    val subtitle: String,
    val artwork: (@Composable () -> Unit)? = null,
)

data class EnoAction(
    val icon: EnoIcons,
    val label: String,
    val onClick: () -> Unit,
)

/** Android v2: один и тот же лист для кнопки «⋮» и долгого нажатия. */
@Composable
fun EnoActionSheet(
    header: EnoActionSheetHeader,
    groups: List<List<EnoAction>>,
    onDismiss: () -> Unit,
) {
    val visibleGroups = groups.filter { it.isNotEmpty() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = EnoRadii.xl, topEnd = EnoRadii.xl),
        dragHandle = {
            Box(
                Modifier.padding(vertical = 12.dp)
                    .width(32.dp).height(4.dp)
                    .background(EnoColors.textSecondary, RoundedCornerShape(percent = 50)),
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().semantics { paneTitle = header.title }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(EnoRadii.s))
                        .background(EnoColors.surface2),
                ) {
                    header.artwork?.invoke()
                    Box(
                        Modifier.matchParentSize().border(
                            EnoElevation.coverBorder, EnoColors.coverOutline,
                            RoundedCornerShape(EnoRadii.s),
                        ),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        header.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = EnoColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        header.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EnoColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = EnoColors.line)
            visibleGroups.forEachIndexed { index, actions ->
                if (index > 0) {
                    HorizontalDivider(
                        color = EnoColors.line,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                actions.forEach { action ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                            .clickable {
                                onDismiss()
                                action.onClick()
                            }
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(action.icon.outline),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = EnoColors.text,
                        )
                        Text(
                            action.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = EnoColors.text,
                        )
                    }
                }
            }
        }
    }
}
