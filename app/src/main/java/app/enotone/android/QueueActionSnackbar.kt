package app.enotone.android

import android.content.res.Resources
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.enotone.playback.QueueEditStatus
import kotlinx.coroutines.flow.collectLatest

internal fun QueueActionMessage.text(resources: Resources): String = when (result.status) {
    QueueEditStatus.APPLIED -> when (notice) {
        QueueStartNotice.FIRST_100 -> resources.getString(R.string.queue_first_100)
        QueueStartNotice.SHUFFLE_100 -> resources.getString(R.string.queue_shuffle_100)
        null -> if (result.count > 1)
            resources.getQuantityString(R.plurals.queue_added_count, result.count, result.count)
        else resources.getString(if (playNext) R.string.queue_will_play_next else R.string.queue_added)
    }
    QueueEditStatus.ALREADY_PLAYING -> resources.getString(R.string.queue_already_playing)
    QueueEditStatus.QUEUE_FULL -> resources.getString(R.string.queue_full)
    QueueEditStatus.EMPTY_SELECTION -> resources.getString(R.string.queue_empty_selection)
    QueueEditStatus.UNAVAILABLE -> resources.getString(R.string.queue_unavailable)
    QueueEditStatus.FAILED -> resources.getString(R.string.queue_edit_failed)
}

@Composable
internal fun rememberQueueSnackbar(model: LibraryViewModel?): SnackbarHostState {
    val host = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model, resources, lifecycle) {
        if (model != null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.queueMessages.collectLatest { message ->
                host.currentSnackbarData?.dismiss()
                host.showSnackbar(message.text(resources), duration = SnackbarDuration.Short)
            }
        }
    }
    return host
}
