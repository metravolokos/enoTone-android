package app.enotone.android.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.android.core.ui.theme.EnoRadii

/**
 * Показывает одну ошибку, которую можно исправить. Видимостью управляет вызывающий код:
 * одновременно он должен показывать не больше одного ModalBottomSheet. Сообщение выводится
 * без изменений и должно уже содержать полное объяснение для пользователя.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnoErrorSheet(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Окно Material может создать свою плотность; сохраняем масштаб шрифта вызывающей композиции.
    val density = LocalDensity.current
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val lightSystemBars = MaterialTheme.colorScheme.background.luminance() >
        ERROR_SHEET_LIGHT_BAR_THRESHOLD
    val maxSheetHeight =
        (LocalConfiguration.current.screenHeightDp * ERROR_SHEET_SCREEN_HEIGHT_FRACTION).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetColor,
        contentColor = EnoColors.text,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = ERROR_SHEET_SCRIM_ALPHA),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        shape = RoundedCornerShape(topStart = EnoRadii.xl, topEnd = EnoRadii.xl),
        // The visible handle is inside the constrained body so it is counted in the 75% limit.
        dragHandle = null,
    ) {
        CompositionLocalProvider(LocalDensity provides density) {
            EnoModalSheetSystemBars(sheetColor, lightSystemBars)
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .navigationBarsPadding()
                    .testTag(ENO_ERROR_SHEET_CONTENT_TAG)
                    .semantics { paneTitle = message },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Box(
                    modifier = Modifier.padding(vertical = ERROR_SHEET_HANDLE_VERTICAL_PADDING)
                        .size(width = ERROR_SHEET_HANDLE_WIDTH, height = ERROR_SHEET_HANDLE_HEIGHT)
                        .background(EnoColors.textSecondary, RoundedCornerShape(percent = 50))
                        .testTag(ENO_ERROR_SHEET_HANDLE_TAG),
                )
                BanditIllustration(
                    pose = BanditPose.RELAXING,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ERROR_SHEET_SIDE_PADDING)
                        .height(ERROR_SHEET_BANDIT_HEIGHT),
                )
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .testTag(ENO_ERROR_SHEET_MESSAGE_TAG)
                        .padding(
                            start = ERROR_SHEET_SIDE_PADDING,
                            top = ERROR_SHEET_MESSAGE_TOP,
                            end = ERROR_SHEET_SIDE_PADDING,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Normal,
                            lineHeight = ERROR_SHEET_MESSAGE_LINE_HEIGHT,
                        ),
                        color = EnoColors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                Button(
                    onClick = onAction,
                    modifier = Modifier.padding(top = ERROR_SHEET_ACTION_TOP)
                        .heightIn(min = ERROR_SHEET_ACTION_HEIGHT)
                        .testTag(ENO_ERROR_SHEET_ACTION_TAG),
                    shape = EnoRadii.full,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(
                        horizontal = ERROR_SHEET_ACTION_HORIZONTAL,
                        vertical = 8.dp,
                    ),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
                Box(Modifier.height(ERROR_SHEET_BOTTOM_SPACING))
            }
        }
    }
}

internal const val ENO_ERROR_SHEET_CONTENT_TAG = "eno_error_sheet_content"
internal const val ENO_ERROR_SHEET_MESSAGE_TAG = "eno_error_sheet_message"
internal const val ENO_ERROR_SHEET_HANDLE_TAG = "eno_error_sheet_handle"
internal const val ENO_ERROR_SHEET_ACTION_TAG = "eno_error_sheet_action"

private const val ERROR_SHEET_SCREEN_HEIGHT_FRACTION = 0.75f
private const val ERROR_SHEET_SCRIM_ALPHA = 0.4f
private const val ERROR_SHEET_LIGHT_BAR_THRESHOLD = 0.5f
private val ERROR_SHEET_HANDLE_VERTICAL_PADDING = 12.dp
private val ERROR_SHEET_HANDLE_WIDTH = 32.dp
private val ERROR_SHEET_HANDLE_HEIGHT = 4.dp
private val ERROR_SHEET_SIDE_PADDING = 36.dp
private val ERROR_SHEET_BANDIT_HEIGHT = 220.dp
private val ERROR_SHEET_MESSAGE_TOP = 10.dp
private val ERROR_SHEET_MESSAGE_LINE_HEIGHT = 23.2.sp
private val ERROR_SHEET_ACTION_TOP = 24.dp
private val ERROR_SHEET_ACTION_HEIGHT = 52.dp
private val ERROR_SHEET_ACTION_HORIZONTAL = 28.dp
private val ERROR_SHEET_BOTTOM_SPACING = 16.dp
