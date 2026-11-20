package app.enotone.android

import app.enotone.library.ManagedIndexProgress

/** Progress shared by both first-run indexing screens. */
internal data class FirstRunProgress(val completed: Int, val total: Int)

internal fun calculateFirstRunProgress(
    importedTrackCount: Int?,
    pendingTagCount: Int?,
    progress: ManagedIndexProgress?,
): FirstRunProgress {
    val total = if (importedTrackCount != null) {
        importedTrackCount.coerceAtLeast(0)
    } else {
        progress?.let { it.attempted + it.remaining } ?: pendingTagCount.orZero()
    }
    val completed = if (importedTrackCount != null) {
        val remaining = progress?.remaining ?: pendingTagCount ?: total
        total - remaining
    } else {
        progress?.attempted ?: 0
    }
    return FirstRunProgress(completed = completed.coerceIn(0, total), total = total)
}

private fun Int?.orZero() = this ?: 0
