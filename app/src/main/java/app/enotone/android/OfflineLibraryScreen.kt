package app.enotone.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.LibraryPage
import app.enotone.library.LibraryPageVisibility
import app.enotone.library.OfflineLibraryGrouping
import app.enotone.library.OfflineTrackCatalog
import app.enotone.library.OfflineTrackRow
import app.enotone.playback.CachePhase
import app.enotone.playback.HandoffOrigin
import app.enotone.playback.HandoffPhase
import app.enotone.playback.HandoffSnapshot
import app.enotone.playback.PlaybackSetupTimings
import app.enotone.playback.PlaybackSeekPolicy
import app.enotone.playback.PlaybackDataIssue
import app.enotone.playback.PlaybackDataIssueClassifier
import app.enotone.playback.TransientCacheStatus
import app.enotone.playback.EnoTonePlaybackService
import app.enotone.playback.QueueTrack
import app.enotone.playback.WebDavPlaybackTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * isPlaying=false also occurs during BUFFERING and after ENDED. Never report
 * those states as a user Pause, or hide an underlying Media3 player error.
 */
private fun media3PlaybackLabel(player: Player?): String {
    if (player == null) return "IDLE"
    if (player.playerError != null) return "ERROR"
    return when (player.playbackState) {
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> if (player.isPlaying) "PLAYING" else "PAUSED"
        Player.STATE_ENDED -> "ENDED"
        else -> "IDLE"
    }
}

/**
 * M2 offline catalogue + M3 one-track MediaSessionService playback.
 * The track list stays local. A tap with RAM-only credentials starts a single
 * stream hosted by the service, which may continue after the screen closes.
 */
@Composable
internal fun OfflineLibraryScreen(
    accountEmail: String,
    appPassword: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var tracks by remember(accountEmail) { mutableStateOf<List<OfflineTrackRow>>(emptyList()) }
    // Store only local already-imported metadata in RAM for quick queue picks.
    // Do not reopen/requery all 3590 Room records on every track tap.
    var preparedById by remember(accountEmail) {
        mutableStateOf<Map<String, QueueTrack>>(emptyMap())
    }
    var search by remember(accountEmail) { mutableStateOf("") }
    var section by remember(accountEmail) { mutableStateOf(LibraryPage.TRACKS) }
    val pagePreferences = remember(context) { LibraryPagePreferences(context) }
    var hiddenPages by remember(context) {
        mutableStateOf(pagePreferences.hiddenPages())
    }
    val visiblePages = remember(hiddenPages) { LibraryPageVisibility.visible(hiddenPages) }
    var showPageSettings by remember { mutableStateOf(false) }
    var selectedGroupKey by remember(accountEmail) { mutableStateOf<String?>(null) }
    var loading by remember(accountEmail) { mutableStateOf(true) }
    var failed by remember(accountEmail) { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedTitle by remember { mutableStateOf<String?>(null) }
    var playbackState by remember { mutableStateOf("IDLE") }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var canSeek by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf(0f) }
    var queuePosition by remember { mutableStateOf(0) }
    var queueTotal by remember { mutableStateOf(0) }
    var repeatMode by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var errorCode by remember { mutableStateOf(0) }
    var errorReadIssue by remember { mutableStateOf(PlaybackDataIssue.UNKNOWN) }
    var playback by remember { mutableStateOf<EnoTonePlaybackService?>(null) }
    // Diagnostic mode belongs only to the next tap, not to a running stream.
    // Accepted M5 mode is the normal default; raw WebDAV remains diagnostic.
    var useCacheOnNextPlay by remember { mutableStateOf(true) }
    var showDiagnostics by remember { mutableStateOf(false) }
    // A two-row mini player stays visible; full controls are in an optional
    // scrollable dialog instead of occupying the whole track-list viewport.
    var showPlayerDetails by remember { mutableStateOf(false) }
    var handoffInfo by remember { mutableStateOf<HandoffSnapshot?>(null) }
    var selectionStartedAt by remember { mutableStateOf(0L) }
    var roomDurationMs by remember { mutableStateOf<Long?>(null) }
    var media3DurationMs by remember { mutableStateOf<Long?>(null) }
    var setupTimings by remember { mutableStateOf<PlaybackSetupTimings?>(null) }
    var loadedQueueSources by remember { mutableStateOf(0) }
    var bufferingAfterTapMs by remember { mutableStateOf<Long?>(null) }
    var readyAfterTapMs by remember { mutableStateOf<Long?>(null) }
    var playingAfterTapMs by remember { mutableStateOf<Long?>(null) }
    var cacheInfo by remember {
        mutableStateOf(TransientCacheStatus(CachePhase.IDLE, 0L, 0L))
    }
    var nextCacheInfo by remember {
        mutableStateOf(TransientCacheStatus(CachePhase.IDLE, 0L, 0L))
    }

    LaunchedEffect(accountEmail) {
        loading = true
        failed = false
        try {
            val (catalogue, prepared) = withContext(Dispatchers.IO) {
                val sourceKey = LibrarySnapshotPlanner.accountKey(accountEmail)
                val db = openLibraryDatabase(context)
                try {
                    val remote = db.remoteTrackDao().allForSource(sourceKey)
                    val catalogue = OfflineTrackCatalog.build(sourceKey, remote)
                    val byId = remote.associateBy { it.id }
                    val prepared = catalogue.mapNotNull { shown ->
                        val item = byId[shown.id]
                        val size = item?.remoteSize
                        if (item == null || item.missingScans != 0 ||
                            size == null || size <= 0L ||
                            runCatching { WebDavPlaybackTarget.url(item.encodedPath) }.isFailure
                        ) null else QueueTrack(
                            id = shown.id,
                            title = shown.title,
                            encodedPath = item.encodedPath,
                            remoteSize = size,
                            remoteEtag = item.remoteEtag,
                        )
                    }.associateBy { it.id }
                    catalogue to prepared
                } finally {
                    db.close()
                }
            }
            preparedById = prepared
            tracks = catalogue
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    // Binding does not start a new stream. Unbinding the UI leaves ongoing
    // playback in the MediaSessionService and its notification.
    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                playback = (binder as? EnoTonePlaybackService.LocalBinder)?.service()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                playback = null
            }
        }
        var bound = false
        try {
            bound = context.bindService(
                Intent(context, EnoTonePlaybackService::class.java)
                    .setAction(EnoTonePlaybackService.ACTION_LOCAL_BIND),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        } catch (_: Exception) {
            playbackState = "ERROR"
            errorCode = 0
        }
        onDispose {
            if (bound) context.unbindService(connection)
            playback = null
        }
    }

    DisposableEffect(playback) {
        val active = playback
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (selectionStartedAt > 0L) {
                    val elapsed = SystemClock.elapsedRealtime() - selectionStartedAt
                    if (state == Player.STATE_BUFFERING && bufferingAfterTapMs == null) {
                        bufferingAfterTapMs = elapsed
                    }
                    if (state == Player.STATE_READY && readyAfterTapMs == null) {
                        readyAfterTapMs = elapsed
                    }
                }
                playbackState = media3PlaybackLabel(active?.player)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && selectionStartedAt > 0L && playingAfterTapMs == null) {
                    playingAfterTapMs =
                        SystemClock.elapsedRealtime() - selectionStartedAt
                }
                playbackState = media3PlaybackLabel(active?.player)
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                selectedTitle = mediaMetadata.title?.toString()
                queuePosition = (active?.queuePosition ?: 0) + 1
                queueTotal = active?.queueSize ?: 0
            }
            override fun onRepeatModeChanged(newMode: Int) {
                repeatMode = newMode
            }
            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                shuffleEnabled = enabled
            }
            override fun onPlayerError(error: PlaybackException) {
                errorCode = error.errorCode
                errorReadIssue = PlaybackDataIssueClassifier.classify(error)
                playbackState = "ERROR"
            }
        }
        active?.player?.addListener(listener)
        if (active != null) {
            selectedTitle = active.player.currentMediaItem?.mediaMetadata?.title?.toString()
            queuePosition = active.queuePosition + 1
            queueTotal = active.queueSize
            repeatMode = active.repeatMode
            shuffleEnabled = active.shuffleEnabled
            playbackState = media3PlaybackLabel(active.player)
        }
        onDispose { active?.player?.removeListener(listener) }
    }

    LaunchedEffect(playback) {
        while (true) {
            cacheInfo = playback?.cacheSnapshot()
                ?: TransientCacheStatus(CachePhase.IDLE, 0L, 0L)
            nextCacheInfo = playback?.nextCacheSnapshot()
                ?: TransientCacheStatus(CachePhase.IDLE, 0L, 0L)
            loadedQueueSources = playback?.loadedQueueSize ?: 0
            handoffInfo = playback?.handoffSnapshot()
            val active = playback
            // The bound service remains the source of truth for position,
            // including after background playback or external media controls.
            positionMs = active?.player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            val availableDuration = active?.player?.duration ?: 0L
            durationMs = if (PlaybackSeekPolicy.isKnownDuration(availableDuration)) {
                availableDuration
            } else 0L
            canSeek = active?.canSeekCurrent == true
            delay(500L)
        }
    }

    // A page hidden in Settings must never leave a stale group or selection
    // visible. This affects only UI state, not playback, Room or downloaded tags.
    LaunchedEffect(section, visiblePages) {
        val fallback = LibraryPageVisibility.selectedOrFallback(section, visiblePages)
        if (fallback != section) {
            section = fallback
            selectedGroupKey = null
            search = ""
        }
    }

    // Pure RAM grouping from the single Room snapshot; never scans cloud files.
    val unknownAlbumName = stringResource(R.string.library_unknown_album)
    val groups = remember(tracks, section, unknownAlbumName) {
        when (section) {
            LibraryPage.ALBUMS ->
                OfflineLibraryGrouping.albums(tracks, unknownAlbumName)
            LibraryPage.ARTISTS -> OfflineLibraryGrouping.artists(tracks)
            LibraryPage.ALBUM_ARTISTS -> OfflineLibraryGrouping.albumArtists(tracks)
            LibraryPage.TRACKS -> emptyList()
        }
    }
    val selectedGroup = groups.firstOrNull { it.key == selectedGroupKey }
    val shownGroups = remember(groups, search) {
        groups.filter { OfflineLibraryGrouping.matches(it, search) }
    }
    // The visible set is also the playback queue context; no Media3 changes.
    val visible = remember(tracks, section, selectedGroup, search) {
        val candidates = when (section) {
            LibraryPage.TRACKS -> tracks
            else -> selectedGroup?.let { group ->
                tracks.filter { it.id in group.trackIds }
            } ?: emptyList()
        }
        OfflineTrackCatalog.search(candidates, search)
    }
    if (showPageSettings) {
        AlertDialog(
            onDismissRequest = { showPageSettings = false },
            title = { Text(stringResource(R.string.library_pages_settings_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.library_pages_settings_explanation),
                        style = MaterialTheme.typography.bodySmall)
                    LibraryPageVisibility.defaultOrder.forEach { page ->
                        val title = when (page) {
                            LibraryPage.TRACKS -> R.string.library_tab_tracks
                            LibraryPage.ALBUMS -> R.string.library_tab_albums
                            LibraryPage.ARTISTS -> R.string.library_tab_artists
                            LibraryPage.ALBUM_ARTISTS -> R.string.library_tab_album_artists
                        }
                        val isVisible = page in visiblePages
                        val canToggle = !isVisible || visiblePages.size > 1
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = isVisible,
                                enabled = canToggle,
                                onCheckedChange = { shouldShow ->
                                    val updated = LibraryPageVisibility.withVisibility(
                                        hiddenPages, page, shouldShow,
                                    )
                                    if (updated != hiddenPages) {
                                        hiddenPages = updated
                                        pagePreferences.saveHiddenPages(updated)
                                    }
                                },
                            )
                            Text(stringResource(title))
                        }
                    }
                    Text(stringResource(R.string.library_pages_settings_last_visible),
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPageSettings = false }) {
                    Text(stringResource(R.string.library_pages_settings_done))
                }
            },
        )
    }
    if (showPlayerDetails && selectedTitle != null) {
        AlertDialog(
            onDismissRequest = { showPlayerDetails = false },
            title = { Text(stringResource(R.string.library_player_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
        OutlinedButton(onClick = { showDiagnostics = !showDiagnostics }) {
            Text(stringResource(
                if (showDiagnostics) R.string.playback_diagnostics_hide
                else R.string.playback_diagnostics_show))
        }
        if (showDiagnostics) {
            Button(
                enabled = !selecting,
                onClick = { useCacheOnNextPlay = !useCacheOnNextPlay },
            ) {
                Text(stringResource(
                    if (useCacheOnNextPlay) R.string.playback_mode_cache
                    else R.string.playback_mode_baseline))
            }
            if (selectionStartedAt > 0L) {
                Text(
                    stringResource(
                        R.string.playback_startup_timing,
                        roomDurationMs?.toString() ?: "—",
                        media3DurationMs?.toString() ?: "—",
                        bufferingAfterTapMs?.toString() ?: "—",
                        readyAfterTapMs?.toString() ?: "—",
                        playingAfterTapMs?.toString() ?: "—",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                setupTimings?.let { stages ->
                    Text(
                        stringResource(
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
            }
        }
        if (selectedTitle != null) {
            Text(selectedTitle!!, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium)
            val status = when (playbackState) {
                "BUFFERING" -> stringResource(R.string.playback_buffering)
                "PLAYING" -> stringResource(R.string.playback_playing)
                "PAUSED" -> stringResource(R.string.playback_paused)
                "ENDED" -> stringResource(R.string.playback_ended)
                "ERROR" -> stringResource(R.string.playback_error, errorCode)
                "MISSING_PASSWORD" -> stringResource(R.string.playback_missing_password)
                "MISSING_TRACK" -> stringResource(R.string.playback_missing_track)
                else -> stringResource(R.string.playback_loading)
            }
            Text(status)
            if (playbackState == "ERROR" && errorCode != 0) {
                Text(stringResource(R.string.playback_error_stage, errorReadIssue.name),
                    style = MaterialTheme.typography.bodySmall)
            }
            val shownFraction = if (isScrubbing) scrubFraction
                else PlaybackSeekPolicy.fraction(positionMs, durationMs)
            val shownPositionMs = if (isScrubbing) {
                PlaybackSeekPolicy.positionForFraction(durationMs, scrubFraction) ?: 0L
            } else positionMs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(PlaybackSeekPolicy.formatTime(shownPositionMs))
                Text(
                    if (PlaybackSeekPolicy.isKnownDuration(durationMs)) {
                        PlaybackSeekPolicy.formatTime(durationMs)
                    } else stringResource(R.string.playback_duration_unknown),
                )
            }
            val seekDescription = stringResource(R.string.playback_seek_slider)
            Slider(
                value = shownFraction,
                onValueChange = { value ->
                    isScrubbing = true
                    scrubFraction = value
                },
                onValueChangeFinished = {
                    playback?.seekToFraction(scrubFraction)
                    isScrubbing = false
                },
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = seekDescription
                },
                enabled = canSeek && !selecting,
            )
            // Advanced telemetry is opt-in even when the full player is open.
            if (showDiagnostics) {
            handoffInfo?.let { handoff ->
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
                    stringResource(
                        R.string.handoff_summary,
                        kind, state,
                        handoff.readyMs?.toString() ?: "—",
                        handoff.playingMs?.toString() ?: "—",
                        if (handoff.preparedNext) stringResource(R.string.handoff_yes)
                        else stringResource(R.string.handoff_no),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (cacheInfo.totalBytes > 0L) {
                val cacheText = when (cacheInfo.phase) {
                    CachePhase.DOWNLOADING -> stringResource(
                        R.string.playback_cache_downloading,
                        cacheInfo.cachedBytes / 1024,
                        cacheInfo.totalBytes / 1024,
                    )
                    CachePhase.COMPLETE -> stringResource(R.string.playback_cache_complete)
                    CachePhase.SKIPPED -> stringResource(R.string.playback_cache_skipped)
                    CachePhase.FAILED -> stringResource(R.string.playback_cache_failed)
                    CachePhase.IDLE -> ""
                }
                if (cacheText.isNotEmpty()) Text(cacheText)
                if (cacheInfo.phase == CachePhase.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { (cacheInfo.cachedBytes.toFloat() /
                            cacheInfo.totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (nextCacheInfo.totalBytes > 0L) {
                val nextText = when (nextCacheInfo.phase) {
                    CachePhase.DOWNLOADING -> stringResource(
                        R.string.playback_next_cache_downloading,
                        nextCacheInfo.cachedBytes / 1024,
                        nextCacheInfo.totalBytes / 1024,
                    )
                    CachePhase.COMPLETE -> stringResource(R.string.playback_next_cache_complete)
                    CachePhase.SKIPPED -> stringResource(R.string.playback_next_cache_skipped)
                    CachePhase.FAILED -> stringResource(R.string.playback_next_cache_failed)
                    CachePhase.IDLE -> ""
                }
                if (nextText.isNotEmpty()) Text(nextText)
                if (nextCacheInfo.phase == CachePhase.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { (nextCacheInfo.cachedBytes.toFloat() /
                            nextCacheInfo.totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            }
            if (queueTotal > 0) {
                Text(stringResource(R.string.playback_queue_position,
                    queuePosition, queueTotal))
                if (showDiagnostics && loadedQueueSources in 1 until queueTotal) {
                    Text(stringResource(R.string.playback_queue_staging,
                        loadedQueueSources, queueTotal),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = playback?.hasPrevious == true && !selecting,
                    onClick = { playback?.playPrevious() }) {
                    Text(stringResource(R.string.playback_previous))
                }
                Button(enabled = playback?.hasNext == true && !selecting,
                    onClick = { playback?.playNext() }) {
                    Text(stringResource(R.string.playback_next))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val repeatLabel = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> stringResource(R.string.playback_repeat_one)
                    Player.REPEAT_MODE_ALL -> stringResource(R.string.playback_repeat_all)
                    else -> stringResource(R.string.playback_repeat_off)
                }
                Button(enabled = playback != null && !selecting && queueTotal > 0,
                    onClick = { playback?.cycleRepeatMode() }) {
                    Text(stringResource(R.string.playback_repeat_control, repeatLabel))
                }
                Button(enabled = playback != null && !selecting && queueTotal > 0,
                    onClick = { playback?.toggleShuffle() }) {
                    Text(if (shuffleEnabled) stringResource(R.string.playback_shuffle_on)
                         else stringResource(R.string.playback_shuffle_off))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = playback != null && !selecting,
                    onClick = { playback?.pause() }) {
                    Text(stringResource(R.string.playback_pause))
                }
                Button(enabled = playback != null && !selecting,
                    onClick = { playback?.resume() }) {
                    Text(stringResource(R.string.playback_resume))
                }
                Button(enabled = playback != null && !selecting,
                    onClick = { playback?.stopPlayback(); playbackState = "ENDED" }) {
                    Text(stringResource(R.string.playback_stop))
                }
            }
        } else {
            Text(stringResource(R.string.playback_hint))
        }

                }
            },
            confirmButton = {
                TextButton(onClick = { showPlayerDetails = false }) {
                    Text(stringResource(R.string.library_player_close))
                }
            },
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.library_back)) }
            Text(
                text = stringResource(when (section) {
                    LibraryPage.TRACKS -> R.string.library_tab_tracks
                    LibraryPage.ALBUMS -> R.string.library_tab_albums
                    LibraryPage.ARTISTS -> R.string.library_tab_artists
                    LibraryPage.ALBUM_ARTISTS -> R.string.library_tab_album_artists
                }),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { showPageSettings = true }) {
                Text(stringResource(R.string.library_pages_short))
            }
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            visiblePages.forEach { page ->
                val title = when (page) {
                    LibraryPage.TRACKS -> R.string.library_tab_tracks
                    LibraryPage.ALBUMS -> R.string.library_tab_albums
                    LibraryPage.ARTISTS -> R.string.library_tab_artists
                    LibraryPage.ALBUM_ARTISTS -> R.string.library_tab_album_artists
                }
                // The selected page must be recognizable without relying on color alone.
                // Use the Material 3 filled/outlined button distinction; no custom palette.
                if (section == page) {
                    Button(enabled = !selecting, onClick = {
                        // Already selected: preserve search and group context.
                    }) { Text(stringResource(title)) }
                } else {
                    OutlinedButton(enabled = !selecting, onClick = {
                        section = page
                        selectedGroupKey = null
                        search = ""
                    }) { Text(stringResource(title)) }
                }
            }
        }
        if (section != LibraryPage.TRACKS && selectedGroup != null) {
            OutlinedButton(onClick = { selectedGroupKey = null; search = "" }) {
                Text(stringResource(R.string.library_group_back))
            }
            Text(selectedGroup.name, style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.library_search)) },
            singleLine = true,
        )
        if (section != LibraryPage.TRACKS && selectedGroup == null) {
            Text(stringResource(R.string.library_group_count, shownGroups.size))
        } else {
            Text(stringResource(R.string.library_count, visible.size,
                if (section == LibraryPage.TRACKS) tracks.size
                else selectedGroup?.trackIds?.size ?: 0))
        }
        when {
            loading -> Text(stringResource(R.string.library_loading))
            failed -> Text(stringResource(R.string.library_failed))
            tracks.isEmpty() -> Text(stringResource(R.string.library_empty))
            section != LibraryPage.TRACKS && selectedGroup == null -> {
                if (shownGroups.isEmpty()) {
                    Text(stringResource(R.string.library_groups_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(shownGroups, key = { it.key }) { group ->
                            Column(modifier = Modifier.fillMaxWidth().clickable {
                                selectedGroupKey = group.key
                                search = ""
                            }.padding(vertical = 4.dp)) {
                                Text(group.name, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                                val summary = listOfNotNull(group.subtitle,
                                    stringResource(R.string.library_group_tracks,
                                        group.trackIds.size)).joinToString(" · ")
                                Text(summary, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { track ->
                    Column(modifier = Modifier.fillMaxWidth().clickable(enabled = !selecting && playback != null) {
                        selectedTitle = track.title
                        if (appPassword.isBlank()) {
                            playbackState = "MISSING_PASSWORD"
                            return@clickable
                        }
                        selecting = true
                        playbackState = "LOADING"
                        selectionStartedAt = SystemClock.elapsedRealtime()
                        errorReadIssue = PlaybackDataIssue.UNKNOWN
                        roomDurationMs = null
                        media3DurationMs = null
                        setupTimings = null
                        bufferingAfterTapMs = null
                        readyAfterTapMs = null
                        playingAfterTapMs = null
                        scope.launch {
                            try {
                                val roomStartedAt = SystemClock.elapsedRealtime()
                                // The only Room read occurred on library opening.
                                // Preserve the user's visible search order in RAM.
                                // No network request or new DB query during queue selection.
                                val queue = visible.mapNotNull { shown ->
                                    preparedById[shown.id]
                                }
                                roomDurationMs =
                                    SystemClock.elapsedRealtime() - roomStartedAt
                                if (queue.none { it.id == track.id }) {
                                    playbackState = "MISSING_TRACK"
                                } else {
                                    val service = playback
                                        ?: throw IllegalStateException("Playback service disconnected")
                                    val media3StartedAt = SystemClock.elapsedRealtime()
                                    service.playQueue(accountEmail, appPassword,
                                        queue, track.id, useCacheOnNextPlay)
                                    media3DurationMs =
                                        SystemClock.elapsedRealtime() - media3StartedAt
                                    setupTimings = service.setupTimings()
                                    loadedQueueSources = service.loadedQueueSize
                                    queueTotal = queue.size
                                    queuePosition = queue.indexOfFirst { it.id == track.id } + 1
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                playbackState = "ERROR"
                                errorCode = 0
                            } finally {
                                selecting = false
                            }
                        }
                    }.padding(vertical = 4.dp)) {
                        Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge)
                        val subtitle = listOfNotNull(track.artist, track.album)
                            .joinToString(" · ")
                        if (subtitle.isNotEmpty()) {
                            Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        // Fixed-height mini player: users can scroll albums/tracks without
        // playback diagnostics, cache counters or seek controls pushing them off-screen.
        if (selectedTitle != null) {
            HorizontalDivider()
            Text(selectedTitle!!, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val compactStatus = when (playbackState) {
                    "BUFFERING" -> stringResource(R.string.playback_buffering)
                    "PLAYING" -> stringResource(R.string.playback_playing)
                    "PAUSED" -> stringResource(R.string.playback_paused)
                    "ERROR" -> stringResource(R.string.playback_error, errorCode)
                    "MISSING_PASSWORD" -> stringResource(R.string.playback_missing_password)
                    "MISSING_TRACK" -> stringResource(R.string.playback_missing_track)
                    "ENDED" -> stringResource(R.string.playback_ended)
                    else -> stringResource(R.string.playback_loading)
                }
                Text(compactStatus, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
                TextButton(
                    enabled = playback != null && !selecting,
                    onClick = {
                        if (playbackState == "PLAYING" || playbackState == "BUFFERING") {
                            playback?.pause()
                        } else {
                            playback?.resume()
                        }
                    },
                ) {
                    Text(stringResource(if (playbackState == "PLAYING" ||
                        playbackState == "BUFFERING") R.string.playback_pause
                        else R.string.playback_resume))
                }
                TextButton(enabled = playback?.hasNext == true && !selecting,
                    onClick = { playback?.playNext() }) {
                    Text(stringResource(R.string.playback_next))
                }
                TextButton(onClick = { showPlayerDetails = true }) {
                    Text(stringResource(R.string.library_player_open))
                }
            }
        }
    }
}
