package app.enotone.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.OfflineTrackCatalog
import app.enotone.library.OfflineTrackRow
import app.enotone.playback.CachePhase
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
    var loading by remember(accountEmail) { mutableStateOf(true) }
    var failed by remember(accountEmail) { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedTitle by remember { mutableStateOf<String?>(null) }
    var playbackState by remember { mutableStateOf("IDLE") }
    var queuePosition by remember { mutableStateOf(0) }
    var queueTotal by remember { mutableStateOf(0) }
    var repeatMode by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var errorCode by remember { mutableStateOf(0) }
    var playback by remember { mutableStateOf<EnoTonePlaybackService?>(null) }
    // Diagnostic mode belongs only to the next tap, not to a running stream.
    var useCacheOnNextPlay by remember { mutableStateOf(false) }
    var selectionStartedAt by remember { mutableStateOf(0L) }
    var roomDurationMs by remember { mutableStateOf<Long?>(null) }
    var media3DurationMs by remember { mutableStateOf<Long?>(null) }
    var bufferingAfterTapMs by remember { mutableStateOf<Long?>(null) }
    var readyAfterTapMs by remember { mutableStateOf<Long?>(null) }
    var playingAfterTapMs by remember { mutableStateOf<Long?>(null) }
    var cacheInfo by remember {
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
                playbackState = when (state) {
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> if (active?.player?.isPlaying == true) "PLAYING" else "PAUSED"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "IDLE"
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && selectionStartedAt > 0L && playingAfterTapMs == null) {
                    playingAfterTapMs =
                        SystemClock.elapsedRealtime() - selectionStartedAt
                }
                playbackState = if (isPlaying) "PLAYING" else "PAUSED"
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
            if (active.player.isPlaying) playbackState = "PLAYING"
        }
        onDispose { active?.player?.removeListener(listener) }
    }

    LaunchedEffect(playback) {
        while (true) {
            cacheInfo = playback?.cacheSnapshot()
                ?: TransientCacheStatus(CachePhase.IDLE, 0L, 0L)
            delay(1_000L)
        }
    }

    val visible = remember(tracks, search) { OfflineTrackCatalog.search(tracks, search) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) { Text(stringResource(R.string.library_back)) }
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.library_search)) },
            singleLine = true,
        )
        Text(stringResource(R.string.library_count, visible.size, tracks.size))
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
            }
            if (queueTotal > 0) {
                Text(stringResource(R.string.playback_queue_position,
                    queuePosition, queueTotal))
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
        when {
            loading -> Text(stringResource(R.string.library_loading))
            failed -> Text(stringResource(R.string.library_failed))
            tracks.isEmpty() -> Text(stringResource(R.string.library_empty))
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
                        roomDurationMs = null
                        media3DurationMs = null
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
    }
}
