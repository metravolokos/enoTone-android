package app.enotone.android

import android.content.ComponentName
import androidx.activity.compose.BackHandler
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.IBinder
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.AlbumTagRefresher
import app.enotone.library.AlbumTagRefreshSelection
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
import app.enotone.playback.QueueStateRestorer
import app.enotone.playback.WebDavPlaybackTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    initialPage: LibraryPage = LibraryPage.TRACKS,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val artworkRepository = remember(context) { ArtworkPreviewRepository(context) }
    var tracks by remember(accountEmail) { mutableStateOf<List<OfflineTrackRow>>(emptyList()) }
    var libraryRefreshEpoch by remember(accountEmail) { mutableStateOf(0) }
    val managedIndexStatus by ManagedScanRuntime.status.collectAsState()
    var albumRefreshBusy by remember(accountEmail) { mutableStateOf(false) }
    var albumCheckedByGroup by remember(accountEmail) {
        mutableStateOf<Map<String, Set<String>>>(emptyMap())
    }
    var albumAttemptedByGroup by remember(accountEmail) {
        mutableStateOf<Map<String, Set<String>>>(emptyMap())
    }
    var confirmAlbumRefresh by remember(accountEmail) {
        mutableStateOf<Pair<String, List<String>>?>(null)
    }
    var albumRefreshFeedback by remember(accountEmail) {
        mutableStateOf<Pair<String, String>?>(null)
    }

    // Store only local already-imported metadata in RAM for quick queue picks.
    // Do not reopen/requery all 3590 Room records on every track tap.
    var preparedById by remember(accountEmail) {
        mutableStateOf<Map<String, QueueTrack>>(emptyMap())
    }
    // One deterministic album image identity across Tracks, Albums, Artists,
    // Album Artists, mini player and Now Playing. Unknown Album is per-track.
    val artworkCatalog = remember(tracks, preparedById, libraryRefreshEpoch) {
        AlbumArtworkCatalog.build(tracks) { id ->
            preparedById[id]?.let { item ->
                PlayingArtworkTarget(
                    id = item.id,
                    encodedPath = item.encodedPath,
                    remoteSize = item.remoteSize,
                    remoteEtag = item.remoteEtag,
                    refreshEpoch = libraryRefreshEpoch,
                )
            }
        }
    }
    var search by remember(accountEmail) { mutableStateOf("") }
    var section by remember(accountEmail, initialPage) { mutableStateOf(initialPage) }
    val pagePreferences = remember(context) { LibraryPagePreferences(context) }
    val hiddenPages = remember(context) { pagePreferences.hiddenPages() }
    val visiblePages = remember(hiddenPages) { LibraryPageVisibility.visible(hiddenPages) }
    var selectedGroupKey by remember(accountEmail) { mutableStateOf<String?>(null) }
    var loading by remember(accountEmail) { mutableStateOf(true) }
    var failed by remember(accountEmail) { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedTitle by remember { mutableStateOf<String?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var currentTrackId by remember { mutableStateOf<String?>(null) }
    var currentArtwork by remember { mutableStateOf<Bitmap?>(null) }
    // Only when Now Playing is open: decode up to 1200px from the permanent master.
    var fullArtwork by remember(currentTrackId, accountEmail) {
        mutableStateOf<Bitmap?>(null)
    }
    var searchExpanded by remember(accountEmail) { mutableStateOf(false) }
    var playbackState by remember { mutableStateOf("IDLE") }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var canSeek by remember { mutableStateOf(false) }
    var queuePosition by remember { mutableStateOf(0) }
    var queueTotal by remember { mutableStateOf(0) }
    var repeatMode by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var errorCode by remember { mutableStateOf(0) }
    var errorReadIssue by remember { mutableStateOf(PlaybackDataIssue.UNKNOWN) }
    var playback by remember { mutableStateOf<EnoTonePlaybackService?>(null) }
    // Diagnostic mode belongs only to the next tap, not to a running stream.
    // Accepted M5 mode is the normal default; raw WebDAV remains diagnostic.
    val useCacheOnNextPlay = PlaybackDiagnosticsRuntime.useCacheOnNextPlay
    // Full Now Playing replaces the library in this composable, but does not
    // unbind MediaSessionService or restart any audio stream.
    var showPlayerDetails by remember { mutableStateOf(false) }
    BackHandler(enabled = showPlayerDetails) { showPlayerDetails = false }
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

    LaunchedEffect(accountEmail, libraryRefreshEpoch) {
        loading = true
        failed = false
        try {
            val (catalogue, prepared) = withContext(Dispatchers.IO) {
                val sourceKey = LibrarySnapshotPlanner.accountKey(accountEmail)
                val db = openLibraryDatabase(context)
                run {
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

    // Restore once a usable local catalogue and an opt-in credential vault exist.
    // Room resolution and vault access run off main; missing accounts/tracks are
    // intentionally silent, and restoration never opens a network connection.
    LaunchedEffect(playback, preparedById, accountEmail, appPassword) {
        val active = playback ?: return@LaunchedEffect
        if (active.queueSize > 0 || accountEmail.isBlank() || appPassword.isBlank() ||
            preparedById.isEmpty()) return@LaunchedEffect
        val saved = withContext(Dispatchers.IO) {
            val savedLogin = runCatching { LocalCredentialVault.restore(context) }.getOrNull()
            if (savedLogin?.email != accountEmail ||
                savedLogin.password != appPassword) null
            else runCatching { active.readSavedQueue() }.getOrNull()
        } ?: return@LaunchedEffect
        val sourceKey = LibrarySnapshotPlanner.accountKey(accountEmail)
        if (saved.sourceKey != sourceKey) return@LaunchedEffect
        val filtered = QueueStateRestorer.filter(saved, preparedById.keys)
            ?: return@LaunchedEffect
        val queue = filtered.trackIds.mapNotNull(preparedById::get)
        if (queue.size != filtered.trackIds.size || active.queueSize > 0) {
            return@LaunchedEffect
        }
        if (runCatching {
                active.restoreQueue(accountEmail, appPassword, queue, filtered,
                    useCacheOnNextPlay)
            }.getOrDefault(false)) {
            val track = tracks.firstOrNull { it.id == filtered.selectedTrackId }
            currentTrackId = filtered.selectedTrackId
            selectedTitle = track?.title ?: queue.first { it.id == filtered.selectedTrackId }.title
            selectedArtist = track?.artist
            queuePosition = filtered.trackIds.indexOf(filtered.selectedTrackId) + 1
            queueTotal = filtered.trackIds.size
            positionMs = filtered.positionMs
            repeatMode = filtered.repeatMode
            shuffleEnabled = filtered.shuffleEnabled
            playbackState = "PAUSED"
        }
    }

    val latestTracks by rememberUpdatedState(tracks)
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
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentTrackId = mediaItem?.mediaId
                selectedArtist = latestTracks.firstOrNull {
                    it.id == mediaItem?.mediaId
                }?.artist
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                selectedTitle = mediaMetadata.title?.toString()
                selectedArtist = mediaMetadata.artist?.toString()
                    ?: latestTracks.firstOrNull { it.id == active?.player?.currentMediaItem?.mediaId }?.artist
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
            currentTrackId = active.player.currentMediaItem?.mediaId
            selectedTitle = active.player.currentMediaItem?.mediaMetadata?.title?.toString()
            selectedArtist = active.player.currentMediaItem?.mediaMetadata?.artist?.toString()
                ?: latestTracks.firstOrNull { it.id == currentTrackId }?.artist
            queuePosition = active.queuePosition + 1
            queueTotal = active.queueSize
            repeatMode = active.repeatMode
            shuffleEnabled = active.shuffleEnabled
            playbackState = media3PlaybackLabel(active.player)
        }
        onDispose { active?.player?.removeListener(listener) }
    }

    // One persistent album image, shared with every other library page.
    // Show resident pixels immediately; defer any WebDAV to audio READY.
    LaunchedEffect(currentTrackId, artworkCatalog, appPassword, accountEmail,
        libraryRefreshEpoch) {
        currentArtwork = null
        val id = currentTrackId ?: return@LaunchedEffect
        val group = artworkCatalog.byTrackId[id] ?: return@LaunchedEffect
        currentArtwork = artworkRepository.peekAlbum(accountEmail, group, 512)
        artworkRepository.cachedAlbumCover(accountEmail, group, 512)?.let {
            currentArtwork = it
        }
        if (appPassword.isBlank()) return@LaunchedEffect
        snapshotFlow { playbackState }.first { state ->
            state == "PLAYING" || state == "PAUSED"
        }
        currentArtwork = artworkRepository.albumCover(accountEmail, appPassword,
            group, requestedSide = 512) ?: currentArtwork
    }

    LaunchedEffect(showPlayerDetails, currentTrackId, artworkCatalog,
        appPassword, accountEmail, libraryRefreshEpoch) {
        fullArtwork = null
        if (!showPlayerDetails) return@LaunchedEffect
        val id = currentTrackId ?: return@LaunchedEffect
        val group = artworkCatalog.byTrackId[id] ?: return@LaunchedEffect
        fullArtwork = artworkRepository.peekAlbum(accountEmail, group, 1200)
        artworkRepository.cachedAlbumCover(accountEmail, group, 1200)?.let {
            fullArtwork = it
        }
        if (appPassword.isBlank()) return@LaunchedEffect
        snapshotFlow { playbackState }.first { state ->
            state == "PLAYING" || state == "PAUSED"
        }
        fullArtwork = artworkRepository.albumCover(accountEmail, appPassword,
            group, requestedSide = 1200) ?: fullArtwork
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
            // Publish anonymous counters for Settings only. It is safe to
            // leave the library: the last in-process snapshot remains
            // available without reconnecting to the Media3 service.
            PlaybackDiagnosticsRuntime.publish(
                PlaybackDiagnosticSnapshot(
                    hasSession = selectedTitle != null,
                    roomMs = roomDurationMs,
                    media3Ms = media3DurationMs,
                    bufferingMs = bufferingAfterTapMs,
                    readyMs = readyAfterTapMs,
                    playingMs = playingAfterTapMs,
                    setup = setupTimings,
                    loadedQueueSources = loadedQueueSources,
                    queueTotal = queueTotal,
                    handoff = handoffInfo,
                    cache = cacheInfo,
                    nextCache = nextCacheInfo,
                    errorCode = errorCode,
                    errorIssue = errorReadIssue,
                )
            )
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
                val members = tracks.filter { it.id in group.trackIds }
                if (section == LibraryPage.ALBUMS) {
                    OfflineTrackCatalog.albumOrder(members)
                } else members
            } ?: emptyList()
        }
        OfflineTrackCatalog.search(candidates, search)
    }
    // Keep scroll positions while Now Playing temporarily replaces the
    // library subtree. Switching a category or search deliberately resets it.
    val libraryListState = remember(section, selectedGroupKey, search) { LazyListState() }
    val albumGridState = remember(section, search) { LazyGridState() }
    // Album header can play the whole group; tapping an individual track
    // retains its existing (possibly search-filtered) queue context.
    // Both use the original Media3 playback path and no extra Room reads.
    val albumTracks = remember(tracks, section, selectedGroup) {
        if (section == LibraryPage.ALBUMS && selectedGroup != null) {
            OfflineTrackCatalog.albumOrder(
                tracks.filter { it.id in selectedGroup.trackIds },
            )
        } else emptyList()
    }
    val albumYear = remember(albumTracks) {
        OfflineTrackCatalog.commonAlbumYear(albumTracks)
    }
    val albumGenre = remember(albumTracks) {
        OfflineTrackCatalog.commonAlbumGenre(albumTracks)
    }
    // Only explicit confirmed batches of <=10 files. Previously successful
    // records are skipped. After network/parse failures, try still-unread
    // album tracks before retrying an unsuccessful ID, without HTTP in Compose.
    val albumRefreshSampleIds = remember(albumTracks, preparedById,
        albumCheckedByGroup, albumAttemptedByGroup, selectedGroup?.key) {
        val eligibleIds = albumTracks.mapNotNull { row ->
            val path = preparedById[row.id]?.encodedPath ?: return@mapNotNull null
            if (path.endsWith(".flac", ignoreCase = true) ||
                path.endsWith(".mp3", ignoreCase = true)) row.id else null
        }
        AlbumTagRefreshSelection.nextBatch(
            eligibleIds,
            albumCheckedByGroup[selectedGroup?.key].orEmpty(),
            albumAttemptedByGroup[selectedGroup?.key].orEmpty(),
        )
    }
    val albumRefreshDoneFormat = stringResource(R.string.album_refresh_done)
    val albumRefreshRetryFormat = stringResource(R.string.album_refresh_retry_needed)
    val albumRefreshBackoffText = stringResource(R.string.album_refresh_backoff)
    val albumRefreshBudgetText = stringResource(R.string.album_refresh_budget)
    val albumRefreshFailedText = stringResource(R.string.album_refresh_failed)
    val playTrack: (OfflineTrackRow, List<OfflineTrackRow>) -> Unit = playTrack@{ track, contextTracks ->
        if (selecting || playback == null) return@playTrack
        selectedTitle = track.title
        selectedArtist = track.artist
        if (appPassword.isBlank()) {
            playbackState = "MISSING_PASSWORD"
            return@playTrack
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
                val queue = contextTracks.mapNotNull { shown ->
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
                        queue, track.id, useCacheOnNextPlay,
                        LibrarySnapshotPlanner.accountKey(accountEmail))
                    currentTrackId = track.id
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
    }
    // Explicit confirmation before ANY album tag HTTP. No silent reindex.
    val albumRequest = confirmAlbumRefresh
    if (albumRequest != null) {
        AlertDialog(
            onDismissRequest = { confirmAlbumRefresh = null },
            title = { Text(stringResource(R.string.album_refresh_confirm_title)) },
            text = { Text(stringResource(R.string.album_refresh_confirm_text,
                albumRequest.second.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAlbumRefresh = null
                    val key = albumRequest.first
                    val ids = albumRequest.second
                    val canStart = selectedGroup?.key == key &&
                        !albumRefreshBusy &&
                        managedIndexStatus.state != ManagedScanState.RUNNING &&
                        !managedIndexStatus.terminating &&
                        playbackState !in setOf("PLAYING", "BUFFERING", "LOADING")
                    if (canStart && ids.isNotEmpty()) {
                        albumRefreshBusy = true
                        albumRefreshFeedback = null
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    val db = openLibraryDatabase(context)
                                    run {
                                        AlbumTagRefresher(db).refresh(
                                            accountEmail, appPassword, ids)
                                    }
                                }
                                // An explicit album refresh also invalidates
                                // artwork even when the server kept the same
                                // ETag/size. Shared picture blobs remain.
                                val refreshedPictures = result.checkedIds.mapNotNull { id ->
                                    preparedById[id]?.let { item ->
                                        PlayingArtworkTarget(
                                            id = item.id,
                                            encodedPath = item.encodedPath,
                                            remoteSize = item.remoteSize,
                                            remoteEtag = item.remoteEtag,
                                        )
                                    }
                                } + artworkCatalog.byGroupKey[key]?.targets.orEmpty()
                                artworkRepository.invalidate(accountEmail, refreshedPictures)
                                albumCheckedByGroup = albumCheckedByGroup +
                                    (key to (albumCheckedByGroup[key].orEmpty() +
                                        result.checkedIds))
                                albumAttemptedByGroup = albumAttemptedByGroup +
                                    (key to (albumAttemptedByGroup[key].orEmpty() +
                                        result.attemptedIds))
                                val unfinished = ids.count { it !in result.checkedIds }
                                val note = when (result.progress.stopCode) {
                                    "RETRY_BACKOFF", "AUTH_REJECTED" ->
                                        albumRefreshBackoffText
                                    "TIME_BUDGET", "NETWORK_BUDGET" ->
                                        albumRefreshBudgetText
                                    else -> ""
                                }
                                val retryInfo = if (unfinished > 0) {
                                    " · " + String.format(albumRefreshRetryFormat, unfinished)
                                } else ""
                                val stopInfo = if (note.isNotEmpty()) " · " + note else ""
                                albumRefreshFeedback = key to (
                                    String.format(
                                        albumRefreshDoneFormat,
                                        result.attemptedIds.size,
                                        result.progress.savedWithTags,
                                    ) + retryInfo + stopInfo
                                )
                                // Single local Room reload only after an explicit
                                // batch. Existing Media3 queue is never restarted.
                                libraryRefreshEpoch++
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                albumRefreshFeedback = key to albumRefreshFailedText
                            } finally {
                                albumRefreshBusy = false
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.album_refresh_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAlbumRefresh = null }) {
                    Text(stringResource(R.string.album_refresh_cancel))
                }
            },
        )
    }
    // A single in-place full-screen player; Back returns to the exact
    // previous library tab/scroll without touching the bound Media3 service.
    if (showPlayerDetails && selectedTitle != null) {
        NowPlayingScreen(
            trackId = currentTrackId.orEmpty(),
            title = selectedTitle.orEmpty(),
            artist = selectedArtist,
            album = remember(tracks, currentTrackId) {
                tracks.firstOrNull { it.id == currentTrackId }?.album
            },
            artwork = fullArtwork ?: currentArtwork,
            playbackState = playbackState,
            errorCode = errorCode,
            positionMs = positionMs,
            durationMs = durationMs,
            canSeek = canSeek,
            controlsEnabled = playback != null && !selecting,
            hasPrevious = playback?.hasPrevious == true,
            hasNext = playback?.hasNext == true,
            queuePosition = queuePosition,
            queueTotal = queueTotal,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            onClose = { showPlayerDetails = false },
            onSeekFraction = { fraction -> playback?.seekToFraction(fraction) },
            onPlayPause = {
                if (playbackState == "PLAYING" || playbackState == "BUFFERING") {
                    playback?.pause()
                } else {
                    playback?.resume()
                }
            },
            onPrevious = { playback?.playPrevious() },
            onNext = { playback?.playNext() },
            onCycleRepeat = { playback?.cycleRepeatMode() },
            onToggleShuffle = { playback?.toggleShuffle() },
        )
    } else {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = {
                    if (selectedGroupKey != null) {
                        selectedGroupKey = null
                        search = ""
                    } else onBack()
                },
                modifier = Modifier.size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        androidx.compose.foundation.shape.CircleShape,
                    ),
            ) {
                Icon(painterResource(R.drawable.ic_action_back),
                    contentDescription = stringResource(R.string.library_group_back),
                    tint = MaterialTheme.colorScheme.onSurface)
            }

        }
        if (selectedGroupKey == null) {
            Text(
                text = stringResource(when (section) {
                    LibraryPage.TRACKS -> R.string.ui_songs_title
                    LibraryPage.ALBUMS -> R.string.library_tab_albums
                    LibraryPage.ARTISTS -> R.string.library_tab_artists
                    LibraryPage.ALBUM_ARTISTS -> R.string.library_tab_album_artists
                }),
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
            )
            BasicTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
                    .background(MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                decorationBox = { innerField ->
                    Box(modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart) {
                        if (search.isEmpty()) {
                            Text(
                                text = stringResource(when (section) {
                                    LibraryPage.TRACKS -> R.string.ui_search_songs
                                    LibraryPage.ALBUMS -> R.string.ui_search_albums
                                    LibraryPage.ARTISTS -> R.string.ui_search_artists
                                    LibraryPage.ALBUM_ARTISTS -> R.string.ui_search_artists
                                }),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 17.sp,
                            )
                        }
                        innerField()
                    }
                },
            )
        }
        if (search.isNotBlank()) {
            val count = if (section != LibraryPage.TRACKS && selectedGroup == null) {
                stringResource(R.string.library_group_count, shownGroups.size)
            } else {
                stringResource(R.string.library_count, visible.size,
                    if (section == LibraryPage.TRACKS) tracks.size
                    else selectedGroup?.trackIds?.size ?: 0)
            }
            Text(count, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if ((section == LibraryPage.TRACKS || section == LibraryPage.ALBUMS) &&
            selectedGroup == null && tracks.isNotEmpty() && search.isBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val enabled = playback != null && !selecting && appPassword.isNotBlank()
                val all = if (section == LibraryPage.TRACKS) visible else tracks
                MusicListAction(
                    text = stringResource(R.string.ui_play),
                    icon = R.drawable.ic_action_play,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { all.firstOrNull()?.let { playTrack(it, all) } },
                )
                MusicListAction(
                    text = stringResource(R.string.library_shuffle_all),
                    icon = R.drawable.ic_action_shuffle,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val shuffled = all.shuffled()
                        shuffled.firstOrNull()?.let { playTrack(it, shuffled) }
                    },
                )
            }
        }
        when {
            loading -> Text(stringResource(R.string.library_loading))
            failed -> Text(stringResource(R.string.library_failed))
            tracks.isEmpty() -> Text(stringResource(R.string.library_empty))
            // Only the album page uses a cover grid; performers remain
            // readable lists until their detailed views are implemented.
            section == LibraryPage.ALBUMS && selectedGroup == null -> {
                if (shownGroups.isEmpty()) {
                    Text(stringResource(R.string.library_groups_empty))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = albumGridState,
                        contentPadding = PaddingValues(bottom = if (selectedTitle != null) 184.dp else 100.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        gridItems(shownGroups, key = { it.key }) { group ->
                            AlbumArtworkTile(
                                group = group,
                                artworkGroup = artworkCatalog.byGroupKey[group.key],
                                repository = artworkRepository,
                                accountEmail = accountEmail,
                                appPassword = appPassword,
                                onOpen = {
                                    selectedGroupKey = group.key
                                    search = ""
                                },
                            )
                        }
                    }
                }
            }
            section != LibraryPage.TRACKS && selectedGroup == null -> {
                if (shownGroups.isEmpty()) {
                    Text(stringResource(R.string.library_groups_empty))
                } else {
                    LazyColumn(
                        state = libraryListState,
                        contentPadding = PaddingValues(bottom = if (selectedTitle != null) 160.dp else 84.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(shownGroups, key = { it.key }) { group ->
                            Row(
                                modifier = Modifier.fillMaxWidth().height(72.dp)
                                    .clickable {
                                        selectedGroupKey = group.key
                                        search = ""
                                    },
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.size(56.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = group.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "›",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = 72.dp),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }
            else -> LazyColumn(
                state = libraryListState,
                        contentPadding = PaddingValues(bottom = if (selectedTitle != null) 160.dp else 84.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (section == LibraryPage.ALBUMS && selectedGroup != null) {
                    item(key = "album-detail-header") {
                        AlbumDetailHeader(
                            group = selectedGroup,
                            year = albumYear,
                            genre = albumGenre,
                            artworkGroup = artworkCatalog.byGroupKey[selectedGroup.key],
                            repository = artworkRepository,
                            accountEmail = accountEmail,
                            appPassword = appPassword,
                            canPlay = albumTracks.isNotEmpty() && !selecting &&
                                !albumRefreshBusy &&
                                playback != null && appPassword.isNotBlank(),
                            onPlayAll = {
                                albumTracks.firstOrNull()?.let { first ->
                                    playTrack(first, albumTracks)
                                }
                            },
                            onShuffleAll = {
                                val shuffled = albumTracks.shuffled()
                                shuffled.firstOrNull()?.let { playTrack(it, shuffled) }
                            },
                            refreshingTags = albumRefreshBusy,
                            canRefreshTags = !albumRefreshBusy &&
                                albumRefreshSampleIds.isNotEmpty() &&
                                appPassword.isNotBlank() &&
                                managedIndexStatus.state != ManagedScanState.RUNNING &&
                                !managedIndexStatus.terminating &&
                                playbackState !in setOf("PLAYING", "BUFFERING", "LOADING"),
                            refreshMessage = when {
                                albumRefreshFeedback?.first == selectedGroup.key ->
                                    albumRefreshFeedback?.second.orEmpty()
                                albumRefreshSampleIds.isEmpty() ->
                                    stringResource(R.string.album_refresh_no_more)
                                playbackState in setOf("PLAYING", "BUFFERING") ->
                                    stringResource(R.string.album_refresh_audio_active)
                                else -> ""
                            },
                            onRefreshTags = {
                                if (!albumRefreshBusy && albumRefreshSampleIds.isNotEmpty()) {
                                    confirmAlbumRefresh = selectedGroup.key to
                                        albumRefreshSampleIds
                                }
                            },
                        )
                    }
                }
                items(visible, key = { it.id }) { track ->
                    // All rows remain lazy. Cover loads only for composed items.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                            .clickable(enabled = !selecting && playback != null) {
                                playTrack(track, visible)
                            },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (section == LibraryPage.ALBUMS && selectedGroup != null) {
                            Text(
                                text = track.trackNumber?.toString().orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(width = 32.dp, height = 24.dp),
                            )
                        } else {
                            TrackArtworkThumbnail(
                                trackId = track.id,
                                artworkGroup = artworkCatalog.byTrackId[track.id],
                                repository = artworkRepository,
                                accountEmail = accountEmail,
                                appPassword = appPassword,
                                allowNetwork = playbackState != "LOADING" &&
                                    playbackState != "BUFFERING",
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            val subtitle = if (section == LibraryPage.ALBUMS &&
                                selectedGroup != null) track.artist.orEmpty()
                            else listOfNotNull(track.artist, track.album)
                                .joinToString(" · ")
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = if (section == LibraryPage.ALBUMS &&
                            selectedGroup != null) 44.dp else 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
    Column(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Native Material 3 mini-player: a raised Surface above the NavigationBar.
        if (selectedTitle != null) {
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
            ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .pointerInput(Unit) {
                        var dragged = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragged = 0f },
                            onVerticalDrag = { change, distance ->
                                dragged += distance
                                change.consume()
                            },
                            onDragEnd = { if (dragged < -55f) showPlayerDetails = true },
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover and metadata both open Now Playing. Two transport
                // actions stay visible; a redundant third "expand" button
                // would take space away from long track titles.
                Box(
                    modifier = Modifier.size(48.dp).clickable(
                        onClickLabel = stringResource(R.string.library_player_open),
                    ) {
                        showPlayerDetails = true
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(6.dp),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (currentArtwork != null) {
                            Image(
                                bitmap = currentArtwork!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_nav_tracks),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
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
                Column(
                    modifier = Modifier.weight(1f).clickable {
                        showPlayerDetails = true
                    }.padding(start = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(selectedTitle!!, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                    val subtitle = if (playbackState in listOf(
                        "ERROR", "MISSING_PASSWORD", "MISSING_TRACK"))
                        compactStatus else selectedArtist.orEmpty()
                    Text(subtitle, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val paused = playbackState != "PLAYING" && playbackState != "BUFFERING"
                IconButton(
                    enabled = playback != null && !selecting,
                    onClick = {
                        if (!paused) playback?.pause() else playback?.resume()
                    },
                ) {
                    Icon(
                        painter = painterResource(if (paused)
                            R.drawable.ic_action_play else R.drawable.ic_action_pause),
                        modifier = Modifier.size(28.dp),
                        contentDescription = stringResource(if (paused)
                            R.string.playback_resume else R.string.playback_pause),
                    )
                }
                IconButton(
                    enabled = playback?.hasNext == true && !selecting,
                    onClick = { playback?.playNext() },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_next),
                        contentDescription = stringResource(R.string.playback_next),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            }
        }
        // Material 3 NavigationBar; category visibility is managed in Settings.
        CompactLibraryNavigation(
            selectedPage = section,
            visiblePages = visiblePages,
            onHome = onBack,
            onPageSelected = { page ->
                if (page != section) {
                    section = page
                    selectedGroupKey = null
                    search = ""
                    searchExpanded = false
                }
            },
            enabled = !selecting,
        )
    }
    }
    }
}
