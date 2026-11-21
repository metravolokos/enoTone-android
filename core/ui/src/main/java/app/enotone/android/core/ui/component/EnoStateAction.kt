package app.enotone.android.core.ui.component

import androidx.compose.runtime.Immutable

/** Действие, которое отображает [EnoStateView]. */
@Immutable
data class EnoStateAction(
    val label: String,
    val onClick: () -> Unit,
    /** Растянуть кнопку на ширину контейнера, например для формы входа. */
    val fillWidth: Boolean = false,
    val enabled: Boolean = true,
)
