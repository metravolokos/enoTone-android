package app.enotone.android.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Плашка «Перемешать всё · N песен» на «Домой», вариант C2 (макет `09_home`, `.shuf2`):
 * 112 dp, радиус `xl`, фон `surface2`; Бандит в наушниках выглядывает снизу справа (108 dp).
 * Бандит декоративный — для TalkBack читается только текст плашки.
 */
@Composable
fun EnoShuffleAllCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(112.dp)
            .clip(RoundedCornerShape(EnoRadii.xl))
            .background(EnoColors.surface2)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(start = 20.dp, end = BANDIT_SPACE),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painter = painterResource(EnoIcons.SHUFFLE.outline),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = EnoColors.text,
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = EnoColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = EnoColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BanditIllustration(
            pose = BanditPose.HEADPHONES,
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-6).dp, y = 10.dp).height(108.dp),
        )
    }
}

/** Место справа под Бандита, чтобы длинный текст не заходил под него. */
private val BANDIT_SPACE = 104.dp
