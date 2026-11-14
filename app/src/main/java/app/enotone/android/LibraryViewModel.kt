package app.enotone.android

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.enotone.library.AlbumTagRefreshSelection
import app.enotone.library.LibraryPage
import app.enotone.library.LibraryPageVisibility
import app.enotone.library.LibraryRepository
import app.enotone.library.LibrarySnapshot
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.LibraryText
import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineLibraryGrouping
import app.enotone.library.OfflineTrackCatalog
import app.enotone.library.OfflineTrackRow
import app.enotone.playback.PlaybackDataIssue
import app.enotone.playback.PlaybackQueue
import app.enotone.playback.QueueEdit
import app.enotone.playback.QueueEditPlanner
import app.enotone.playback.QueueEditResult
import app.enotone.playback.QueueEditStatus
import app.enotone.playback.QueueLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// Display sizes match the unchanged Graphite Material 3 design.
private const val LIBRARY_THUMBNAIL_SIDE_PX = 512
private const val LIBRARY_FULL_ARTWORK_SIDE_PX = 1200
// System.nanoTime is measured in nanoseconds; UI telemetry uses milliseconds.
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val SEARCH_DEBOUNCE_MS = 150L

/** Immutable input for Compose; the queue and credentials never enter this model. */
internal data class LibraryUiState(
    val section: LibraryPage = LibraryPage.TRACKS,
    val visiblePages: List<LibraryPage> = LibraryPageVisibility.defaultOrder,
    val search: String = "",
    val selectedGroupKey: String? = null,
    val tracks: List<OfflineTrackRow> = emptyList(),
    val groups: List<OfflineLibraryGroup> = emptyList(),
    val shownGroups: List<OfflineLibraryGroup> = emptyList(),
    val selectedGroup: OfflineLibraryGroup? = null,
    val visible: List<OfflineTrackRow> = emptyList(),
    val albumTracks: List<OfflineTrackRow> = emptyList(),
    val albumYear: Int? = null,
    val albumGenre: String? = null,
    val albumRefreshSampleIds: List<String> = emptyList(),
    val artworkCatalog: AlbumArtworkCatalog = AlbumArtworkCatalog(emptyList(), emptyMap(), emptyMap()),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val selecting: Boolean = false,
    val albumRefreshBusy: Boolean = false,
    val albumRefreshFeedback: Pair<String, String>? = null,
    val confirmAlbumRefresh: Pair<String, List<String>>? = null,
    val managedScanStatus: ManagedScanStatus = ManagedScanStatus(),
    val showPlayerDetails: Boolean = false,
    val currentTrackId: String? = null,
    val selectedTitle: String? = null,
    val selectedArtist: String? = null,
    val currentArtwork: Bitmap? = null,
    val fullArtwork: Bitmap? = null,
    val playbackConnected: Boolean = false,
    val playbackState: String = "IDLE",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val canSeek: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val queuePosition: Int = 0,
    val queueTotal: Int = 0,
    val repeatMode: Int = 0,
    val shuffleEnabled: Boolean = false,
    val errorCode: Int = 0,
    val errorReadIssue: PlaybackDataIssue = PlaybackDataIssue.UNKNOWN,
)

// One interface intentionally groups all screen events for Compose preview/test fakes.
@Suppress("TooManyFunctions")
internal interface LibraryScreenActions {
    fun onSearch(query: String)
    fun onPageSelected(page: LibraryPage)
    fun onGroupSelected(key: String?)
    fun onTrackClicked(track: OfflineTrackRow, context: List<OfflineTrackRow>)
    fun onPlayAll()
    fun onShuffleAll()
    fun onAlbumPlayAll()
    fun onAlbumShuffleAll()
    fun onRequestAlbumRefresh(key: String, ids: List<String>)
    fun onDismissAlbumRefresh()
    fun onConfirmAlbumRefresh()
    fun onPlayerDetails(visible: Boolean)
    fun onPlayPause()
    fun onNext()
    fun onPrevious()
    fun onRepeat()
    fun onShuffle()
    fun onSeekFraction(fraction: Float)
}

internal data class QueueActionMessage(val playNext: Boolean, val result: QueueEditResult)

internal data class AlbumRefreshMessages(
    val done: String = "",
    val retry: String = "",
    val backoff: String = "",
    val budget: String = "",
    val failed: String = "",
)

/** Keep bitmap I/O outside Compose and make ViewModel unit tests Android-free. */
internal interface LibraryArtworkLoader {
    fun peek(email: String, group: AlbumArtworkGroup, size: Int): Bitmap?
    suspend fun cached(email: String, group: AlbumArtworkGroup, size: Int): Bitmap?
    suspend fun fetch(email: String, password: String, group: AlbumArtworkGroup, size: Int): Bitmap?
    suspend fun invalidate(email: String, targets: Collection<PlayingArtworkTarget>)
}
internal class RepositoryArtworkLoader(private val repository: ArtworkPreviewRepository) :
    LibraryArtworkLoader {
    override fun peek(email: String, group: AlbumArtworkGroup, size: Int) =
        repository.peekAlbum(email, group, size)
    override suspend fun cached(email: String, group: AlbumArtworkGroup, size: Int) =
        repository.cachedAlbumCover(email, group, size)
    override suspend fun fetch(email: String, password: String, group: AlbumArtworkGroup, size: Int) =
        repository.albumCover(email, password, group, requestedSide = size)
    override suspend fun invalidate(email: String, targets: Collection<PlayingArtworkTarget>) =
        repository.invalidate(email, targets)
}

/**
 * Loads one Room snapshot, derives searchable pages on IO, owns player
 * actions. The Activity-scoped PlayerViewModel alone restores queues.
 */
// The manual DI constructor preserves test injection; screen operations remain cohesive.
@Suppress("TooManyFunctions", "LongParameterList")
internal class LibraryViewModel(
    private val accountEmail: String,
    private val password: String,
    initialPage: LibraryPage,
    private val repository: LibraryRepository,
    private val connection: PlaybackConnection,
    private val artwork: LibraryArtworkLoader,
    private val io: CoroutineDispatcher,
    private val unknownAlbumName: String,
    private val messages: AlbumRefreshMessages,
    private val useCache: () -> Boolean = { true },
    private val clockMs: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
    visiblePages: List<LibraryPage> = LibraryPageVisibility.defaultOrder,
    private val managedStatus: StateFlow<ManagedScanStatus> = ManagedScanRuntime.status,
    private val accountEvents: StateFlow<AccountState> = MutableStateFlow(AccountState()),
    private val dataRevision: StateFlow<Long> = LibraryDataRevision.value,
) : ViewModel(), LibraryScreenActions {
    private val firstPage = LibraryPageVisibility.selectedOrFallback(initialPage, visiblePages)
    private val mutable = MutableStateFlow(
        LibraryUiState(section = firstPage, visiblePages = visiblePages),
    )
    val state: StateFlow<LibraryUiState> = mutable
    private val mutableQueueMessages = MutableSharedFlow<QueueActionMessage>(extraBufferCapacity = 1)
    val queueMessages: SharedFlow<QueueActionMessage> = mutableQueueMessages
    private var catalogue = LibrarySnapshot(emptyList(), emptyMap())
    private val sourceKey by lazy { LibrarySnapshotPlanner.accountKey(accountEmail) }
    private val checkedByGroup = mutableMapOf<String, Set<String>>()
    private val attemptedByGroup = mutableMapOf<String, Set<String>>()
    private var filtersJob: Job? = null
    private var artworkJob: Job? = null
    private var fullArtworkJob: Job? = null
    private var filterVersion = 0
    private var artworkEpoch = 0
    private var selectionStartedAt = 0L
    private var roomDurationMs: Long? = null
    private var media3DurationMs: Long? = null
    private var setupTimings: app.enotone.playback.PlaybackSetupTimings? = null
    private var bufferingAfterTapMs: Long? = null
    private var readyAfterTapMs: Long? = null
    private var playingAfterTapMs: Long? = null
    private var accountBlocked = accountEvents.value.blocks(sourceKey)
    private var handledOpenRequest: Int? = null
    private var reloadJob: Job? = null

    private fun unavailable(): Boolean = accountEvents.value.blocks(sourceKey)

    /** Cancel callbacks and forget all in-memory tags, groups and artwork. */
    private fun forgetAccount() {
        filtersJob?.cancel()
        artworkJob?.cancel()
        fullArtworkJob?.cancel()
        filterVersion++
        artworkEpoch++
        catalogue = LibrarySnapshot(emptyList(), emptyMap())
        checkedByGroup.clear()
        attemptedByGroup.clear()
        selectionStartedAt = 0L
        roomDurationMs = null
        media3DurationMs = null
        mutable.update { last ->
            LibraryUiState(section = last.section, visiblePages = last.visiblePages,
                managedScanStatus = last.managedScanStatus, loading = false)
        }
    }

    init {
        viewModelScope.launch {
            accountEvents.collect { event ->
                val blocked = event.blocks(sourceKey)
                if (blocked) {
                    accountBlocked = true
                    forgetAccount()
                } else if (accountBlocked) {
                    accountBlocked = false
                    reload()
                }
            }
        }
        viewModelScope.launch {
            connection.state.collect { snapshot ->
                if (!unavailable()) applyPlayback(snapshot)
            }
        }
        viewModelScope.launch {
            managedStatus.collect { status ->
                mutable.update { it.copy(managedScanStatus = status) }
            }
        }
        viewModelScope.launch {
            // Аудит п. 2: импорт, индексация, обложки и обновление альбома меняют Room —
            // снимок перечитывается без пересоздания экрана. Первое значение — начальная загрузка ниже.
            dataRevision.drop(1).collect {
                artworkEpoch++
                reload()
            }
        }
        // App-scoped PlayerViewModel owns binding/unbinding and restoration.
        reload()
    }

    private fun reload() {
        if (unavailable()) {
            forgetAccount()
            return
        }
        // Несколько сигналов подряд (импорт, ревизия, альбом) дают одно чтение Room.
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            if (unavailable()) return@launch
            mutable.update { it.copy(loading = true, failed = false) }
            try {
                val result = repository.load(accountEmail)
                if (unavailable()) return@launch
                catalogue = result
                val covers = withContext(io) {
                    AlbumArtworkCatalog.build(result.tracks) { id ->
                        result.preparedById[id]?.let { item ->
                            PlayingArtworkTarget(item.id, item.encodedPath,
                                item.remoteSize, item.remoteEtag, artworkEpoch)
                        }
                    }
                }
                if (unavailable()) return@launch
                mutable.update {
                    it.copy(tracks = result.tracks, artworkCatalog = covers,
                        loading = false, failed = false)
                }
                derivePages()
                refreshArtwork()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    // Page state is derived together to avoid divergent search/group filters.
    @Suppress("CyclomaticComplexMethod")
    private fun derivePages() {
        filtersJob?.cancel()
        val generation = ++filterVersion
        val snapshot = mutable.value
        val byId = catalogue.preparedById
        filtersJob = viewModelScope.launch {
            val ftsExpression = if (snapshot.section == LibraryPage.TRACKS)
                LibraryText.ftsMatchExpression(snapshot.search) else null
            val matchedIds = if (ftsExpression != null) {
                delay(SEARCH_DEBOUNCE_MS) // Debounce while retaining the last results until the response.
                try {
                    repository.searchTrackIds(accountEmail, snapshot.search)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null // Local fallback keeps the screen usable if the index cannot be queried.
                }
            } else null
            val result = withContext(io) {
                calculateDerivedPages(snapshot, byId, ftsExpression, matchedIds)
            }
            if (generation == filterVersion && !unavailable()) {
                mutable.update { current ->
                    current.copy(groups = result.groups, shownGroups = result.shown,
                        selectedGroup = result.group, visible = result.visible,
                        albumTracks = result.album, albumYear = result.year,
                        albumGenre = result.genre, albumRefreshSampleIds = result.refreshIds)
                }
            }
        }
    }

    /** Page projection stays off the UI dispatcher. */
    private fun calculateDerivedPages(
        snapshot: LibraryUiState,
        byId: Map<String, app.enotone.playback.QueueTrack>,
        ftsExpression: String?,
        matchedIds: Set<String>?,
    ): DerivedPages {
        val groups = when (snapshot.section) {
            LibraryPage.TRACKS -> emptyList()
            LibraryPage.ALBUMS ->
                OfflineLibraryGrouping.albums(snapshot.tracks, unknownAlbumName)
            LibraryPage.ARTISTS -> OfflineLibraryGrouping.artists(snapshot.tracks)
            LibraryPage.ALBUM_ARTISTS -> OfflineLibraryGrouping.albumArtists(snapshot.tracks)
        }
        val group = groups.firstOrNull { it.key == snapshot.selectedGroupKey }
        val shown = groups.filter { OfflineLibraryGrouping.matches(it, snapshot.search) }
        val candidates = when (snapshot.section) {
            LibraryPage.TRACKS -> snapshot.tracks
            else -> group?.let { selected ->
                val members = snapshot.tracks.filter { it.id in selected.trackIds }
                if (snapshot.section == LibraryPage.ALBUMS)
                    OfflineTrackCatalog.albumOrder(members) else members
            } ?: emptyList()
        }
        val visible = visibleForPage(snapshot, candidates, ftsExpression, matchedIds)
        val album = if (snapshot.section == LibraryPage.ALBUMS && group != null)
            OfflineTrackCatalog.albumOrder(
                snapshot.tracks.filter { it.id in group.trackIds },
            ) else emptyList()
        val eligible = eligibleAlbumTrackIds(album, byId)
        val next = AlbumTagRefreshSelection.nextBatch(
            eligible,
            checkedByGroup[group?.key].orEmpty(),
            attemptedByGroup[group?.key].orEmpty(),
        )
        return DerivedPages(groups, shown, group, visible, album,
            OfflineTrackCatalog.commonAlbumYear(album),
            OfflineTrackCatalog.commonAlbumGenre(album), next)
    }

    private fun visibleForPage(
        snapshot: LibraryUiState,
        candidates: List<OfflineTrackRow>,
        ftsExpression: String?,
        matchedIds: Set<String>?,
    ): List<OfflineTrackRow> {
        if (snapshot.section != LibraryPage.TRACKS) {
            return OfflineTrackCatalog.search(candidates, snapshot.search)
        }
        return when {
            ftsExpression == null -> candidates
            matchedIds != null -> candidates.filter { it.id in matchedIds }
            else -> OfflineTrackCatalog.search(candidates, snapshot.search)
        }
    }

    private fun eligibleAlbumTrackIds(
        album: List<OfflineTrackRow>,
        byId: Map<String, app.enotone.playback.QueueTrack>,
    ): List<String> = album.mapNotNull { row ->
        val path = byId[row.id]?.encodedPath ?: return@mapNotNull null
        if (path.endsWith(".flac", true) || path.endsWith(".mp3", true)) row.id
        else null
    }

    private data class DerivedPages(
        val groups: List<OfflineLibraryGroup>,
        val shown: List<OfflineLibraryGroup>,
        val group: OfflineLibraryGroup?,
        val visible: List<OfflineTrackRow>,
        val album: List<OfflineTrackRow>,
        val year: Int?,
        val genre: String?,
        val refreshIds: List<String>,
    )

    override fun onSearch(query: String) {
        mutable.update { it.copy(search = query) }
        derivePages()
    }

    override fun onPageSelected(page: LibraryPage) {
        if (page !in mutable.value.visiblePages || mutable.value.section == page) return
        mutable.update {
            it.copy(section = page, search = "", selectedGroupKey = null)
        }
        derivePages()
    }

    /**
     * Аудит п. 3: переход с «Домой» — событие существующей ViewModel. Каждый переход имеет номер,
     * поэтому возврат из «Сейчас играет» (тот же номер) не сбрасывает выбранную страницу (O-13).
     */
    fun onOpenRequest(request: Int, page: LibraryPage) {
        if (handledOpenRequest == request) return
        val first = handledOpenRequest == null
        handledOpenRequest = request
        if (!first) onPageSelected(LibraryPageVisibility.selectedOrFallback(page, mutable.value.visiblePages))
    }

    /** Аудит п. 3: настройки видимости страниц применяются к уже созданной медиатеке. */
    fun onVisiblePagesChanged(pages: List<LibraryPage>) {
        if (pages.isEmpty() || pages == mutable.value.visiblePages) return
        val section = LibraryPageVisibility.selectedOrFallback(mutable.value.section, pages)
        val moved = section != mutable.value.section
        mutable.update {
            if (moved) it.copy(visiblePages = pages, section = section, search = "", selectedGroupKey = null)
            else it.copy(visiblePages = pages)
        }
        if (moved) derivePages()
    }

    override fun onGroupSelected(key: String?) {
        mutable.update { it.copy(selectedGroupKey = key, search = "") }
        derivePages()
    }

    override fun onPlayAll() {
        val value = mutable.value
        val all = if (value.section == LibraryPage.TRACKS) value.visible else value.tracks
        all.firstOrNull()?.let { onTrackClicked(it, all) }
    }
    override fun onShuffleAll() {
        val value = mutable.value
        val all = (if (value.section == LibraryPage.TRACKS) value.visible
            else value.tracks).shuffled()
        all.firstOrNull()?.let { onTrackClicked(it, all) }
    }

    /** «Перемешать всё» на «Домой» (D3): вся медиатека, независимо от страницы и поиска. */
    fun onShuffleLibrary() {
        val all = mutable.value.tracks.shuffled()
        all.firstOrNull()?.let { onTrackClicked(it, all) }
    }

    override fun onAlbumPlayAll() {
        val items = mutable.value.albumTracks
        items.firstOrNull()?.let { onTrackClicked(it, items) }
    }
    override fun onAlbumShuffleAll() {
        val items = mutable.value.albumTracks.shuffled()
        items.firstOrNull()?.let { onTrackClicked(it, items) }
    }

    /** Backend-only actions for D4a/8b: do not add them to LibraryScreenActions yet. */
    fun onPlayNext(tracks: List<OfflineTrackRow>) = editQueue(tracks, playNext = true)

    fun onAppendToQueue(tracks: List<OfflineTrackRow>) = editQueue(tracks, playNext = false)

    private fun editQueue(rows: List<OfflineTrackRow>, playNext: Boolean) {
        if (unavailable()) return
        viewModelScope.launch {
            val result = try {
                val tracks = withContext(io) {
                    rows.mapNotNull { catalogue.preparedById[it.id] }.distinctBy { it.id }
                }
                when {
                    tracks.isEmpty() -> QueueEditResult(QueueEditStatus.EMPTY_SELECTION)
                    password.isBlank() || !connection.state.value.connected ->
                        QueueEditResult(QueueEditStatus.UNAVAILABLE)
                    connection.state.value.queueTotal == 0 -> {
                        val selection = tracks.take(PlaybackQueue.MAX_TRACKS)
                        val plan = QueueEditPlanner.plan(
                            QueueLayout(emptyList(), null, null),
                            QueueEdit.Append(selection.map { it.id }),
                        )
                        connection.playQueue(accountEmail, password, selection, selection.first().id,
                            useCache(), sourceKey)
                        if (tracks.size > PlaybackQueue.MAX_TRACKS)
                            QueueEditResult(QueueEditStatus.QUEUE_FULL, selection.size)
                        else plan.result
                    }
                    playNext -> connection.insertNext(tracks)
                    else -> connection.append(tracks)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                QueueEditResult(QueueEditStatus.UNAVAILABLE)
            }
            mutableQueueMessages.emit(QueueActionMessage(playNext, result))
        }
    }

    override fun onTrackClicked(track: OfflineTrackRow, context: List<OfflineTrackRow>) {
        if (unavailable() || mutable.value.selecting || !mutable.value.playbackConnected) return
        mutable.update { it.copy(selectedTitle = track.title, selectedArtist = track.artist) }
        if (password.isBlank()) {
            mutable.update { it.copy(playbackState = "MISSING_PASSWORD") }
            return
        }
        selectionStartedAt = clockMs()
        roomDurationMs = null
        media3DurationMs = null
        setupTimings = null
        bufferingAfterTapMs = null
        readyAfterTapMs = null
        playingAfterTapMs = null
        mutable.update {
            it.copy(selecting = true, playbackState = "LOADING",
                errorReadIssue = PlaybackDataIssue.UNKNOWN)
        }
        viewModelScope.launch {
            try {
                if (unavailable()) return@launch
                val started = clockMs()
                val queue = withContext(io) {
                    context.mapNotNull { catalogue.preparedById[it.id] }
                }
                roomDurationMs = clockMs() - started
                if (unavailable()) return@launch
                if (queue.none { it.id == track.id }) {
                    mutable.update { it.copy(playbackState = "MISSING_TRACK") }
                } else {
                    val startedPlayer = clockMs()
                    connection.playQueue(accountEmail, password, queue, track.id,
                        useCache(), sourceKey)
                    media3DurationMs = clockMs() - startedPlayer
                    setupTimings = connection.state.value.setupTimings
                    mutable.update {
                        it.copy(currentTrackId = track.id, queueTotal = queue.size,
                            queuePosition = queue.indexOfFirst { entry ->
                                entry.id == track.id
                            } + 1)
                    }
                    refreshArtwork()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(playbackState = "ERROR", errorCode = 0) }
            } finally {
                mutable.update { it.copy(selecting = false) }
            }
        }
    }

    // Translate one complete Media3 snapshot atomically for the UI.
    @Suppress("CyclomaticComplexMethod")
    private fun applyPlayback(snapshot: PlaybackSnapshot) {
        if (unavailable()) return
        val prior = mutable.value
        val playbackState = if (prior.selecting) prior.playbackState
            else snapshot.playbackState
        val elapsed = clockMs() - selectionStartedAt
        if (selectionStartedAt > 0L && snapshot.playbackState == "BUFFERING" &&
            bufferingAfterTapMs == null) bufferingAfterTapMs = elapsed
        if (selectionStartedAt > 0L && snapshot.playbackState in setOf("PLAYING", "PAUSED") &&
            readyAfterTapMs == null) readyAfterTapMs = elapsed
        if (selectionStartedAt > 0L && snapshot.playbackState == "PLAYING" &&
            playingAfterTapMs == null) playingAfterTapMs = elapsed
        val nextId = snapshot.currentTrackId ?: prior.currentTrackId
        mutable.update { old ->
            old.copy(
                playbackConnected = snapshot.connected,
                playbackState = playbackState,
                currentTrackId = nextId,
                selectedTitle = snapshot.title ?: old.selectedTitle,
                selectedArtist = if (snapshot.currentTrackId == null) old.selectedArtist
                    else snapshot.artist
                        ?: old.tracks.firstOrNull { it.id == snapshot.currentTrackId }?.artist,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                canSeek = snapshot.canSeek,
                hasPrevious = snapshot.hasPrevious,
                hasNext = snapshot.hasNext,
                queuePosition = if (snapshot.queueTotal > 0)
                    snapshot.queuePosition else old.queuePosition,
                queueTotal = if (snapshot.queueTotal > 0)
                    snapshot.queueTotal else old.queueTotal,
                repeatMode = snapshot.repeatMode,
                shuffleEnabled = snapshot.shuffleEnabled,
                errorCode = snapshot.errorCode,
                errorReadIssue = snapshot.errorReadIssue,
            )
        }
        if (nextId != prior.currentTrackId) refreshArtwork()
        publishDiagnostics(snapshot)
    }

    private fun publishDiagnostics(snapshot: PlaybackSnapshot) {
        PlaybackDiagnosticsRuntime.publish(PlaybackDiagnosticSnapshot(
            hasSession = mutable.value.selectedTitle != null,
            roomMs = roomDurationMs,
            media3Ms = media3DurationMs,
            bufferingMs = bufferingAfterTapMs,
            readyMs = readyAfterTapMs,
            playingMs = playingAfterTapMs,
            setup = setupTimings ?: snapshot.setupTimings,
            loadedQueueSources = snapshot.loadedQueueSources,
            queueTotal = mutable.value.queueTotal,
            handoff = snapshot.handoffInfo,
            cache = snapshot.cacheInfo,
            nextCache = snapshot.nextCacheInfo,
            errorCode = mutable.value.errorCode,
            errorIssue = mutable.value.errorReadIssue,
        ))
    }

    private fun refreshArtwork() {
        if (unavailable()) return
        artworkJob?.cancel()
        fullArtworkJob?.cancel()
        val value = mutable.value
        val id = value.currentTrackId
        val group = value.artworkCatalog.byTrackId[id]
        artworkJob = viewModelScope.launch {
            mutable.update { it.copy(currentArtwork = null) }
            if (group == null) return@launch
            artwork.peek(accountEmail, group, LIBRARY_THUMBNAIL_SIDE_PX)?.let { picture ->
                mutable.update { it.copy(currentArtwork = picture) }
            }
            artwork.cached(accountEmail, group, LIBRARY_THUMBNAIL_SIDE_PX)?.let { picture ->
                mutable.update { it.copy(currentArtwork = picture) }
            }
            if (password.isBlank()) return@launch
            state.map { it.playbackState }.first {
                it == "PLAYING" || it == "PAUSED"
            }
            val picture = artwork.fetch(accountEmail, password, group, LIBRARY_THUMBNAIL_SIDE_PX)
            if (picture != null) mutable.update { it.copy(currentArtwork = picture) }
        }
        if (value.showPlayerDetails) loadFullArtwork()
    }

    private fun loadFullArtwork() {
        if (unavailable()) return
        fullArtworkJob?.cancel()
        val value = mutable.value
        val group = value.artworkCatalog.byTrackId[value.currentTrackId]
        fullArtworkJob = viewModelScope.launch {
            mutable.update { it.copy(fullArtwork = null) }
            if (group == null) return@launch
            artwork.peek(accountEmail, group, LIBRARY_FULL_ARTWORK_SIDE_PX)?.let { picture ->
                mutable.update { it.copy(fullArtwork = picture) }
            }
            artwork.cached(accountEmail, group, LIBRARY_FULL_ARTWORK_SIDE_PX)?.let { picture ->
                mutable.update { it.copy(fullArtwork = picture) }
            }
            if (password.isBlank()) return@launch
            state.map { it.playbackState }.first {
                it == "PLAYING" || it == "PAUSED"
            }
            val picture = artwork.fetch(accountEmail, password, group, LIBRARY_FULL_ARTWORK_SIDE_PX)
            if (picture != null) mutable.update { it.copy(fullArtwork = picture) }
        }
    }

    override fun onRequestAlbumRefresh(key: String, ids: List<String>) {
        mutable.update { it.copy(confirmAlbumRefresh = key to ids) }
    }
    override fun onDismissAlbumRefresh() {
        mutable.update { it.copy(confirmAlbumRefresh = null) }
    }
    // Guard conditions preserve the album-only refresh budget and selected group.
    @Suppress("CyclomaticComplexMethod", "ComplexCondition")
    override fun onConfirmAlbumRefresh() {
        if (unavailable()) return
        val request = mutable.value.confirmAlbumRefresh ?: return
        mutable.update { it.copy(confirmAlbumRefresh = null) }
        val current = mutable.value
        val key = request.first
        val ids = request.second
        if (current.selectedGroup?.key != key || current.albumRefreshBusy ||
            current.managedScanStatus.state == ManagedScanState.RUNNING ||
            current.managedScanStatus.terminating ||
            current.playbackState in setOf("PLAYING", "BUFFERING", "LOADING") ||
            ids.isEmpty()) return
        mutable.update { it.copy(albumRefreshBusy = true, albumRefreshFeedback = null) }
        viewModelScope.launch {
            try {
                val result = repository.refreshAlbum(accountEmail, password, ids)
                if (unavailable()) return@launch
                val refreshed = result.checkedIds.mapNotNull { id ->
                    catalogue.preparedById[id]?.let {
                        PlayingArtworkTarget(it.id, it.encodedPath, it.remoteSize, it.remoteEtag)
                    }
                } + mutable.value.artworkCatalog.byGroupKey[key]?.targets.orEmpty()
                artwork.invalidate(accountEmail, refreshed)
                checkedByGroup[key] = checkedByGroup[key].orEmpty() + result.checkedIds
                attemptedByGroup[key] = attemptedByGroup[key].orEmpty() + result.attemptedIds
                val unfinished = ids.count { it !in result.checkedIds }
                val note = when (result.progress.stopCode) {
                    "RETRY_BACKOFF", "AUTH_REJECTED" -> messages.backoff
                    "TIME_BUDGET", "NETWORK_BUDGET" -> messages.budget
                    else -> ""
                }
                val retry = if (unfinished > 0)
                    " · " + String.format(Locale.getDefault(), messages.retry, unfinished)
                    else ""
                val stop = if (note.isNotEmpty()) " · $note" else ""
                val feedback = String.format(Locale.getDefault(), messages.done,
                    result.attemptedIds.size, result.progress.savedWithTags) + retry + stop
                mutable.update { it.copy(albumRefreshFeedback = key to feedback) }
                artworkEpoch++
                reload()
                // Ревизия общая: плеер тоже перечитает каталог; повторный reload здесь отменит текущий.
                LibraryDataRevision.bump()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(albumRefreshFeedback = key to messages.failed) }
            } finally {
                mutable.update { it.copy(albumRefreshBusy = false) }
            }
        }
    }

    override fun onPlayerDetails(visible: Boolean) {
        mutable.update { it.copy(showPlayerDetails = visible) }
        if (visible) loadFullArtwork() else fullArtworkJob?.cancel()
    }
    override fun onPlayPause() = connection.playPause()
    override fun onNext() = connection.playNext()
    override fun onPrevious() = connection.playPrevious()
    override fun onRepeat() = connection.cycleRepeatMode()
    override fun onShuffle() = connection.toggleShuffle()
    override fun onSeekFraction(fraction: Float) = connection.seekToFraction(fraction)

    companion object {
        // Factory keeps manual dependency injection explicit for JVM tests.
        @Suppress("LongParameterList")
        fun factory(
            email: String,
            password: String,
            initialPage: LibraryPage,
            container: AppContainer,
            unknownAlbumName: String,
            messages: AlbumRefreshMessages,
            visiblePages: List<LibraryPage>,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == LibraryViewModel::class.java)
                return LibraryViewModel(email, password, initialPage,
                    container.libraryRepository, container.playbackConnection,
                    RepositoryArtworkLoader(container.artworkPreviewRepository),
                    container.io, unknownAlbumName, messages,
                    visiblePages = visiblePages,
                    accountEvents = container.accountSession.state) as T
            }
        }
    }
}
