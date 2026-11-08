package app.enotone.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
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
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.OfflineTrackCatalog
import app.enotone.library.OfflineTrackRow
import app.enotone.playback.EnoTonePlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    var search by remember(accountEmail) { mutableStateOf("") }
    var loading by remember(accountEmail) { mutableStateOf(true) }
    var failed by remember(accountEmail) { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedTitle by remember { mutableStateOf<String?>(null) }
    var playbackState by remember { mutableStateOf("IDLE") }
    var errorCode by remember { mutableStateOf(0) }
    var playback by remember { mutableStateOf<EnoTonePlaybackService?>(null) }

    LaunchedEffect(accountEmail) {
        loading = true
        failed = false
        try {
            tracks = withContext(Dispatchers.IO) {
                val sourceKey = LibrarySnapshotPlanner.accountKey(accountEmail)
                val db = openLibraryDatabase(context)
                try {
                    OfflineTrackCatalog.build(sourceKey,
                        db.remoteTrackDao().allForSource(sourceKey))
                } finally {
                    db.close()
                }
            }
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
                playbackState = when (state) {
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> if (active?.player?.isPlaying == true) "PLAYING" else "PAUSED"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "IDLE"
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playbackState = if (isPlaying) "PLAYING" else "PAUSED"
            }
            override fun onPlayerError(error: PlaybackException) {
                errorCode = error.errorCode
                playbackState = "ERROR"
            }
        }
        active?.player?.addListener(listener)
        if (active != null) {
            selectedTitle = active.player.currentMediaItem?.mediaMetadata?.title?.toString()
            if (active.player.isPlaying) playbackState = "PLAYING"
        }
        onDispose { active?.player?.removeListener(listener) }
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
                        scope.launch {
                            try {
                                val selected = withContext(Dispatchers.IO) {
                                    val sourceKey = LibrarySnapshotPlanner.accountKey(accountEmail)
                                    val db = openLibraryDatabase(context)
                                    try {
                                        db.remoteTrackDao().playableById(track.id, sourceKey)
                                    } finally {
                                        db.close()
                                    }
                                }
                                val remoteSize = selected?.remoteSize
                                if (selected == null || remoteSize == null) {
                                    playbackState = "MISSING_TRACK"
                                } else {
                                    playback?.play(accountEmail, appPassword,
                                        selected.encodedPath, remoteSize,
                                        selected.remoteEtag, track.title)
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
