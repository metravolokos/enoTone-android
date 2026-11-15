package app.enotone.android.core.ui.component

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.android.core.ui.theme.EnoElevation
import app.enotone.android.core.ui.theme.EnoRadii
import kotlinx.coroutines.launch

/** Содержимое листа не знает о медиатеке, очереди или навигации. */
data class EnoActionSheetHeader(
    val title: String,
    val subtitle: String,
    val artwork: (@Composable () -> Unit)? = null,
)

data class EnoAction(
    val icon: EnoIcons?,
    val label: String,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
/** Android v2: один и тот же лист для кнопки «⋮» и долгого нажатия. */
@Composable
fun EnoActionSheet(
    header: EnoActionSheetHeader,
    groups: List<List<EnoAction>>,
    onDismiss: () -> Unit,
) {
    val visibleGroups = groups.filter { it.isNotEmpty() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val lightSystemBars = MaterialTheme.colorScheme.background.luminance() > LIGHT_SYSTEM_BAR_THRESHOLD

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetColor,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = ACTION_SHEET_SCRIM_ALPHA),
        // Insets применяем к содержимому сами, чтобы фон листа доходил до полосы жестов.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        shape = RoundedCornerShape(topStart = EnoRadii.xl, topEnd = EnoRadii.xl),
        dragHandle = {
            Box(
                Modifier.testTag(ACTION_SHEET_HANDLE_TAG).padding(vertical = 12.dp)
                    .width(32.dp).height(4.dp)
                    .background(EnoColors.textSecondary, RoundedCornerShape(percent = 50)),
            )
        },
    ) {
        ActionSheetSystemBars(sheetColor, lightSystemBars)
        Column(
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .testTag(ACTION_SHEET_CONTENT_TAG)
                .semantics { paneTitle = header.title },
        ) {
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
                                // Выход из листа должен анимироваться до смены маршрута/очереди.
                                scope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                    action.onClick()
                                }
                            }
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        action.icon?.let { icon ->
                            Icon(
                                painter = painterResource(icon.outline),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = EnoColors.text,
                            )
                        }
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

/**
 * Material3 ModalBottomSheet открывает отдельное Dialog-окно. Edge-to-edge и цвета
 * MainActivity на него не действуют: на Huawei исчезали часы и белела полоса жестов.
 */
@Composable
private fun ActionSheetSystemBars(sheetColor: Color, lightStatusBars: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        // В отличие от окна Activity, Material3 Dialog может наследовать fullscreen от OEM.
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = sheetColor.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setActionSheetSystemBars(
            window = window,
            lightStatusBars = lightStatusBars,
            lightNavigationBars = sheetColor.luminance() > LIGHT_SYSTEM_BAR_THRESHOLD,
        )
    }
}

/** Прямые API Android вместо AndroidX Core: :core:ui не зависит от WindowCompat в release. */
private fun setActionSheetSystemBars(
    window: Window,
    lightStatusBars: Boolean,
    lightNavigationBars: Boolean,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        val appearance =
            (if (lightStatusBars) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0) or
                (if (lightNavigationBars) WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0)
        window.insetsController?.apply {
            show(android.view.WindowInsets.Type.statusBars())
            setSystemBarsAppearance(appearance, mask)
        }
    } else {
        // minSdk 26 поддерживает обе светлые иконки; остальные флаги окна сохраняем.
        @Suppress("DEPRECATION")
        val oldFlags = window.decorView.systemUiVisibility
        @Suppress("DEPRECATION")
        val lightMask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        @Suppress("DEPRECATION")
        val edgeToEdge = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (oldFlags and lightMask.inv()) or edgeToEdge or
                (if (lightStatusBars) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0) or
                (if (lightNavigationBars) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
    }
}

private const val LIGHT_SYSTEM_BAR_THRESHOLD = 0.5f
private const val ACTION_SHEET_SCRIM_ALPHA = 0.4f
internal const val ACTION_SHEET_HANDLE_TAG = "eno_action_sheet_handle"
internal const val ACTION_SHEET_CONTENT_TAG = "eno_action_sheet_content"
