package app.enotone.android.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.android.core.ui.theme.EnoRadii

/**
 * Общая иллюстрация, заголовок, сообщение, необязательный слот и действие для состояний Android v2.
 * Вызывающий экран задаёт верхний отступ; содержимое прокручивается при нехватке места.
 * Варианты акцентного и вторичного действия взаимоисключающие.
 */
@Composable
fun EnoStateView(
    pose: BanditPose,
    title: String,
    message: String,
    primaryAction: EnoStateAction? = null,
    secondaryAction: EnoStateAction? = null,
    modifier: Modifier = Modifier,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    require(primaryAction == null || secondaryAction == null) {
        "EnoStateView accepts at most one action"
    }

    val action = primaryAction ?: secondaryAction
    val actionIsPrimary = primaryAction != null
    Column(
        modifier = modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = STATE_SIDE_PADDING)
            .testTag(ENO_STATE_VIEW_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        BanditIllustration(
            pose = pose,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(pose.stateIllustrationHeight()),
        )
        Text(
            text = title,
            modifier = Modifier.padding(top = STATE_TITLE_TOP).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = EnoColors.text,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = STATE_MESSAGE_TOP),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Normal,
                lineHeight = STATE_MESSAGE_LINE_HEIGHT,
            ),
            color = EnoColors.textSecondary,
            textAlign = TextAlign.Center,
        )
        content?.invoke(this)
        action?.let {
            Button(
                onClick = it.onClick,
                enabled = it.enabled,
                modifier = Modifier.padding(top = STATE_ACTION_TOP)
                    .heightIn(min = STATE_ACTION_HEIGHT)
                    .then(if (it.fillWidth) Modifier.fillMaxWidth() else Modifier),
                shape = EnoRadii.full,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (actionIsPrimary) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        EnoColors.surface2
                    },
                    contentColor = if (actionIsPrimary) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        EnoColors.text
                    },
                ),
                contentPadding = PaddingValues(horizontal = STATE_ACTION_HORIZONTAL, vertical = 8.dp),
            ) {
                Text(
                    text = it.label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun BanditPose.stateIllustrationHeight() = when (this) {
    BanditPose.SITTING -> 200.dp
    BanditPose.READING, BanditPose.HAPPY -> 240.dp
    BanditPose.THINKING -> 210.dp
    BanditPose.PEEKING,
    BanditPose.BOOKS,
    BanditPose.SLEEPING,
    BanditPose.SLEEPING_BOOKS,
    BanditPose.ARMCHAIR,
    BanditPose.IDEA,
    BanditPose.HEADPHONES -> 230.dp
    BanditPose.RELAXING -> 220.dp
}

private val STATE_SIDE_PADDING = 36.dp
private val STATE_TITLE_TOP = 18.dp
private val STATE_MESSAGE_TOP = 10.dp
private val STATE_MESSAGE_LINE_HEIGHT = 23.2.sp
private val STATE_ACTION_TOP = 24.dp
private val STATE_ACTION_HEIGHT = 52.dp
private val STATE_ACTION_HORIZONTAL = 28.dp

internal const val ENO_STATE_VIEW_TAG = "eno_state_view"
