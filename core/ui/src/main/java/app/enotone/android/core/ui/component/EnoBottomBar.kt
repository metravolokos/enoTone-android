package app.enotone.android.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.android.core.ui.theme.EnoRadii

/**
 * Плавающая нижняя панель Android v2: 70 dp, полностью скруглённая, фон `surface2`.
 * Выбранный пункт — залитый значок `_fill` и подпись цветом акцента (макет `02_songs`).
 */
@Composable
fun EnoBottomBar(
    items: List<EnoBottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(70.dp)
            .clip(EnoRadii.full)
            .background(EnoColors.surface2)
            .padding(horizontal = 6.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val color = if (selected) EnoColors.accentText else EnoColors.textSecondary
            Column(
                modifier = Modifier.weight(1f).height(62.dp)
                    .clip(EnoRadii.full)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    painter = painterResource(if (selected) item.icon.selected else item.icon.outline),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = color,
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Пункт нижней панели: значок UI Icons v1 и подпись, переданная экраном (строки живут в :app). */
data class EnoBottomBarItem(val icon: EnoIcons, val label: String)
