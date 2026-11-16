package app.enotone.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** D3.5: док не анимируется и не исчезает при открытии плеера. Его накрывает шторка. */
@Composable
internal fun AppDockMotion(
    miniVisible: Boolean,
    miniPlayer: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    if (miniVisible) {
        Box(Modifier.fillMaxWidth().height(MINI_HEIGHT).testTag("app-mini-dock")) {
            miniPlayer()
        }
    }
    Box(Modifier.testTag("app-bottom-dock")) {
        bottomBar()
    }
}
