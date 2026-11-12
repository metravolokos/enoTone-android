package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.enotone.playback.CachePhase
import app.enotone.playback.HandoffOrigin
import app.enotone.playback.HandoffPhase
import app.enotone.playback.HandoffSnapshot
import app.enotone.playback.PlaybackDataIssue
import app.enotone.playback.PlaybackSetupTimings
import app.enotone.playback.QueuePersistenceDiagnostics
import app.enotone.playback.TransientCacheStatus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Numeric, anonymous, in-process diagnostics only. The library screen publishes
 * snapshots; Settings reads the last one without opening audio, Room or WebDAV.
 * Never place file names, credentials, media IDs, paths or tag values here.
 */
internal data class PlaybackDiagnosticSnapshot(
    val hasSession: Boolean = false,
    val roomMs: Long? = null,
    val media3Ms: Long? = null,
    val bufferingMs: Long? = null,
    val readyMs: Long? = null,
    val playingMs: Long? = null,
    val setup: PlaybackSetupTimings? = null,
    val loadedQueueSources: Int = 0,
    val queueTotal: Int = 0,
    val handoff: HandoffSnapshot? = null,
    val cache: TransientCacheStatus = TransientCacheStatus(CachePhase.IDLE, 0L, 0L),
    val nextCache: TransientCacheStatus = TransientCacheStatus(CachePhase.IDLE, 0L, 0L),
    val errorCode: Int = 0,
    val errorIssue: PlaybackDataIssue = PlaybackDataIssue.UNKNOWN,
)

internal object PlaybackDiagnosticsRuntime {
    val status = MutableStateFlow(PlaybackDiagnosticSnapshot())

    // Advanced Settings owns the A/B toggle; normal listening defaults to the
    // accepted temporary audio cache. Never expose this on Now Playing.
    var useCacheOnNextPlay by mutableStateOf(true)

    fun publish(snapshot: PlaybackDiagnosticSnapshot) {
        status.value = snapshot
    }
}

/** Only rendered inside the Settings' collapsed technical section. */
@Composable
internal fun PlaybackDiagnosticsPanel() {
    val status by PlaybackDiagnosticsRuntime.status.collectAsState()
    val queue by QueuePersistenceDiagnostics.state.collectAsState()
    val temporaryCache = PlaybackDiagnosticsRuntime.useCacheOnNextPlay
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.playback_settings_diagnostics),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(onClick = {
            PlaybackDiagnosticsRuntime.useCacheOnNextPlay = !temporaryCache
        }) {
            Text(stringResource(
                if (temporaryCache) R.string.playback_mode_cache
                else R.string.playback_mode_baseline,
            ))
        }
        Text(
            text = stringResource(R.string.playback_queue_restore_summary,
                queue.result.name, queue.readResult.name, queue.layoutWrites,
                queue.positionWrites, queue.writeErrors, queue.lastExceptionClass),
            style = MaterialTheme.typography.bodySmall,
        )
        if (!status.hasSession) {
            Text(stringResource(R.string.playback_diagnostics_no_session),
                style = MaterialTheme.typography.bodySmall)
            return@Column
        }

        Text(
            text = stringResource(
                R.string.playback_startup_timing,
                status.roomMs?.toString() ?: "—",
                status.media3Ms?.toString() ?: "—",
                status.bufferingMs?.toString() ?: "—",
                status.readyMs?.toString() ?: "—",
                status.playingMs?.toString() ?: "—",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        status.setup?.restoreSeedMs?.let { millis ->
            Text(
                text = stringResource(R.string.playback_restore_seed_ms, millis),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        status.setup?.let { stages ->
            Text(
                text = stringResource(
                    R.string.playback_setup_breakdown,
                    stages.queueMs,
                    stages.routesAndCacheMs,
                    stages.seedSourcesMs,
                    stages.setSourcesMs,
                    stages.prepareAndPlayMs,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        status.handoff?.let { handoff ->
            val kind = when (handoff.origin) {
                HandoffOrigin.NEXT_BUTTON -> stringResource(R.string.handoff_next_button)
                HandoffOrigin.PREVIOUS_BUTTON -> stringResource(R.string.handoff_previous_button)
                HandoffOrigin.AUTO -> stringResource(R.string.handoff_auto)
                HandoffOrigin.MEDIA_SESSION -> stringResource(R.string.handoff_media_session)
            }
            val state = when (handoff.phase) {
                HandoffPhase.BUFFERING -> stringResource(R.string.handoff_waiting)
                HandoffPhase.READY -> stringResource(R.string.handoff_ready)
                HandoffPhase.FAILED -> stringResource(R.string.handoff_failed)
            }
            Text(
                text = stringResource(
                    R.string.handoff_summary,
                    kind,
                    state,
                    handoff.readyMs?.toString() ?: "—",
                    handoff.playingMs?.toString() ?: "—",
                    if (handoff.preparedNext) stringResource(R.string.handoff_yes)
                    else stringResource(R.string.handoff_no),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (status.loadedQueueSources in 1 until status.queueTotal) {
            Text(
                text = stringResource(
                    R.string.playback_queue_staging,
                    status.loadedQueueSources,
                    status.queueTotal,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (status.errorCode != 0) {
            Text(
                text = stringResource(R.string.playback_error_stage, status.errorIssue.name),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        AudioCacheDiagnostics(status.cache, next = false)
        AudioCacheDiagnostics(status.nextCache, next = true)
    }
}

@Composable
private fun AudioCacheDiagnostics(cache: TransientCacheStatus, next: Boolean) {
    if (cache.totalBytes <= 0L) return
    val message = when (cache.phase) {
        CachePhase.DOWNLOADING -> if (next) stringResource(
            R.string.playback_next_cache_downloading,
            cache.cachedBytes / 1024,
            cache.totalBytes / 1024,
        ) else stringResource(
            R.string.playback_cache_downloading,
            cache.cachedBytes / 1024,
            cache.totalBytes / 1024,
        )
        CachePhase.COMPLETE -> stringResource(
            if (next) R.string.playback_next_cache_complete else R.string.playback_cache_complete)
        CachePhase.SKIPPED -> stringResource(
            if (next) R.string.playback_next_cache_skipped else R.string.playback_cache_skipped)
        CachePhase.FAILED -> stringResource(
            if (next) R.string.playback_next_cache_failed else R.string.playback_cache_failed)
        CachePhase.IDLE -> ""
    }
    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
    if (cache.phase == CachePhase.DOWNLOADING) {
        LinearProgressIndicator(
            progress = { (cache.cachedBytes.toFloat() /
                cache.totalBytes).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
