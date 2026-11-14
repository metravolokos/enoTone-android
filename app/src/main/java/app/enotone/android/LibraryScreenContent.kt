package app.enotone.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.component.EnoCollapsingStrip
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.android.core.ui.component.EnoPill
import app.enotone.android.core.ui.component.EnoRoundButton
import app.enotone.android.core.ui.component.enoBlurSource
import app.enotone.android.core.ui.component.rememberEnoBlurState
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.android.core.ui.theme.EnoRadii
import app.enotone.library.LibraryPage
import java.text.NumberFormat
import androidx.compose.foundation.lazy.grid.items as gridItems

/**
 * Медиатека внутри каркаса D3: заголовок, поиск и кнопки прокручиваются вместе со списком
 * (вариант B), при уходе заголовка вверх появляется полоска с размытием. Нижняя панель и
 * мини-плеер — в каркасе (AppShell); содержимое строк прежнее до D4.
 */
// Один draw-only Compose-узел на все страницы до D4: разбиение по экранам — в D4 (альбом, исполнитель).
@Suppress("CyclomaticComplexMethod", "ComplexCondition", "LongMethod")
@Composable
internal fun LibraryScreenContent(
    state: LibraryUiState,
    actions: LibraryScreenActions,
    artworkRepository: ArtworkPreviewRepository,
    accountEmail: String,
    appPassword: String,
    contentPadding: PaddingValues = PaddingValues(),
    scrollToTop: Int = 0,
) {
    val section = state.section
    val visiblePages = state.visiblePages
    val tracks = state.tracks
    val groups = state.groups
    val shownGroups = state.shownGroups
    val selectedGroup = state.selectedGroup
    val selectedGroupKey = state.selectedGroupKey
    val visible = state.visible
    val albumTracks = state.albumTracks
    val albumYear = state.albumYear
    val albumGenre = state.albumGenre
    val search = state.search
    val loading = state.loading
    val failed = state.failed
    val selecting = state.selecting
    val albumRefreshBusy = state.albumRefreshBusy
    val albumRefreshSampleIds = state.albumRefreshSampleIds
    val albumRefreshFeedback = state.albumRefreshFeedback
    val managedIndexStatus = state.managedScanStatus
    val artworkCatalog = state.artworkCatalog
    val playbackState = state.playbackState
    // Saveable: the app-level Now Playing screen removes the library from
    // composition; MainActivity keeps these states in a SaveableStateHolder so
    // Back returns to the same scroll position (Huawei regression after #92).
    // Без ключа по поиску: поле поиска теперь внутри списка (вариант B), новый state на каждую
    // букву пересоздавал бы список вместе с полем.
    val libraryListState = rememberSaveable(section, selectedGroupKey,
        saver = LazyListState.Saver) { LazyListState() }
    val albumGridState = rememberSaveable(section,
        saver = LazyGridState.Saver) { LazyGridState() }

    val albumRequest = state.confirmAlbumRefresh
    if (albumRequest != null) {
        AlertDialog(
            onDismissRequest = { actions.onDismissAlbumRefresh() },
            title = { Text(stringResource(R.string.album_refresh_confirm_title)) },
            text = { Text(stringResource(R.string.album_refresh_confirm_text,
                albumRequest.second.size)) },
            confirmButton = {
                TextButton(onClick = { actions.onConfirmAlbumRefresh() }) {
                    Text(stringResource(R.string.album_refresh_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { actions.onDismissAlbumRefresh() }) {
                    Text(stringResource(R.string.album_refresh_cancel))
                }
            },
        )
    }

    val blur = rememberEnoBlurState()
    val density = LocalDensity.current
    val stripAfterPx = with(density) { STRIP_AFTER.toPx() }
    val stripVisible by remember(libraryListState, albumGridState, section, selectedGroup) {
        derivedStateOf {
            val grid = section == LibraryPage.ALBUMS && selectedGroup == null
            if (grid) albumGridState.firstVisibleItemIndex > 0 ||
                albumGridState.firstVisibleItemScrollOffset > stripAfterPx
            else libraryListState.firstVisibleItemIndex > 0 ||
                libraryListState.firstVisibleItemScrollOffset > stripAfterPx
        }
    }
    // Системное «Назад» из альбома или исполнителя возвращает к списку страницы, а не на «Домой».
    BackHandler(enabled = selectedGroupKey != null) { actions.onGroupSelected(null) }
    // Повтор активной вкладки: из группы — к списку, иначе — в начало. Обработанный номер сохраняется,
    // чтобы возврат из «Сейчас играет» (тот же номер) не прокручивал список заново.
    var handledScroll by rememberSaveable { mutableIntStateOf(scrollToTop) }
    LaunchedEffect(scrollToTop) {
        if (scrollToTop == handledScroll) return@LaunchedEffect
        handledScroll = scrollToTop
        if (selectedGroupKey != null) {
            actions.onGroupSelected(null)
        } else {
            libraryListState.animateScrollToItem(0)
            albumGridState.animateScrollToItem(0)
        }
    }
    // Поиск раскрывается по значку (макеты 02/04/06/07), а не висит постоянным полем; открыт,
    // пока в нём есть текст. Закрытие — крестик или системное «Назад»; фильтр сбрасывается.
    var searchOpen by rememberSaveable(section) { mutableStateOf(search.isNotEmpty()) }
    val searchFocus = remember { FocusRequester() }
    val closeSearch = {
        searchOpen = false
        actions.onSearch("")
    }
    BackHandler(enabled = searchOpen && selectedGroupKey == null) { closeSearch() }
    var searchScrollRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(searchScrollRequest) {
        if (searchScrollRequest > 0) {
            libraryListState.animateScrollToItem(0)
            albumGridState.animateScrollToItem(0)
        }
    }
    val openSearch: () -> Unit = {
        searchOpen = true
        searchScrollRequest += 1
    }
    // «Играть»/«Перемешать» — только «Песни» и альбом (на «Альбомах», «Артистах», «Авторах» их нет).
    val canPlayPage = section == LibraryPage.TRACKS && selectedGroup == null && tracks.isNotEmpty() &&
        search.isBlank()
    val canPlayAlbum = section == LibraryPage.ALBUMS && selectedGroup != null && albumTracks.isNotEmpty()
    val playEnabled = state.playbackConnected && !selecting && appPassword.isNotBlank()
    val header: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(horizontal = HEADER_EXTRA_PADDING)) {
            if (selectedGroupKey == null) {
                if (searchOpen) {
                    LibrarySearchField(
                        value = search,
                        hint = stringResource(when (section) {
                            LibraryPage.TRACKS -> R.string.ui_search_songs
                            LibraryPage.ALBUMS -> R.string.ui_search_albums
                            LibraryPage.ARTISTS, LibraryPage.ALBUM_ARTISTS -> R.string.ui_search_artists
                        }),
                        focusRequester = searchFocus,
                        onValueChange = { actions.onSearch(it) },
                        onClose = closeSearch,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = stringResource(sectionTitle(section)),
                            style = MaterialTheme.typography.headlineLarge,
                            color = EnoColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(top = 8.dp),
                        )
                        EnoRoundButton(
                            icon = EnoIcons.SEARCH,
                            contentDescription = stringResource(R.string.ui_search),
                            onClick = openSearch,
                            filled = false,
                        )
                    }
                }
                // Счётчик под заголовком; при поиске — «показано N из M» (прежняя строка).
                val countText = when {
                    search.isNotBlank() && section != LibraryPage.TRACKS ->
                        stringResource(R.string.library_group_count, shownGroups.size)
                    search.isNotBlank() -> stringResource(R.string.library_count, visible.size, tracks.size)
                    else -> sectionCount(section, if (section == LibraryPage.TRACKS) tracks.size else groups.size)
                }
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
                    color = EnoColors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 18.dp),
                )
            }
            if (canPlayPage) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EnoPill(
                        label = stringResource(R.string.ui_play), accent = true, icon = EnoIcons.PLAY,
                        enabled = playEnabled, modifier = Modifier.weight(1f),
                        onClick = { actions.onPlayAll() },
                    )
                    EnoPill(
                        label = stringResource(R.string.library_shuffle_all), icon = EnoIcons.SHUFFLE,
                        enabled = playEnabled, modifier = Modifier.weight(1f),
                        onClick = { actions.onShuffleAll() },
                    )
                }
            }
        }
    }
    LaunchedEffect(searchOpen) {
        if (searchOpen) searchFocus.requestFocus()
    }
    val stripTitle = selectedGroup?.name ?: stringResource(sectionTitle(section))

    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        when {
            loading -> { header(); Text(stringResource(R.string.library_loading)) }
            failed -> { header(); Text(stringResource(R.string.library_failed)) }
            tracks.isEmpty() -> { header(); Text(stringResource(R.string.library_empty)) }
            // Only the album page uses a cover grid; performers remain
            // readable lists until their detailed views are implemented.
            section == LibraryPage.ALBUMS && selectedGroup == null -> {
                // Пустой результат поиска — внутри того же списка: поле поиска не пересоздаётся
                // и не теряет фокус, пока пользователь печатает.
                run {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = albumGridState,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxWidth().weight(1f).enoBlurSource(blur),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) { header() }
                        if (shownGroups.isEmpty()) {
                            item(key = EMPTY_KEY, span = { GridItemSpan(maxLineSpan) }) {
                                Text(stringResource(R.string.library_groups_empty))
                            }
                        }
                        gridItems(shownGroups, key = { it.key }) { group ->
                            AlbumArtworkTile(
                                group = group,
                                artworkGroup = artworkCatalog.byGroupKey[group.key],
                                repository = artworkRepository,
                                accountEmail = accountEmail,
                                appPassword = appPassword,
                                onOpen = { actions.onGroupSelected(group.key) },
                            )
                        }
                    }
                }
            }
            section != LibraryPage.TRACKS && selectedGroup == null -> {
                run {
                    LazyColumn(
                        state = libraryListState,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxWidth().weight(1f).enoBlurSource(blur),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = HEADER_KEY) { header() }
                        if (shownGroups.isEmpty()) {
                            item(key = EMPTY_KEY) { Text(stringResource(R.string.library_groups_empty)) }
                        }
                        items(shownGroups, key = { it.key }) { group ->
                            Row(
                                modifier = Modifier.fillMaxWidth().height(72.dp)
                                    .clickable { actions.onGroupSelected(group.key) },
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
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxWidth().weight(1f).enoBlurSource(blur),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = HEADER_KEY) { header() }
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
                                state.playbackConnected && appPassword.isNotBlank(),
                            onPlayAll = { actions.onAlbumPlayAll() },
                            onShuffleAll = { actions.onAlbumShuffleAll() },
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
                            onRefreshTags = { actions.onRequestAlbumRefresh(selectedGroup.key, albumRefreshSampleIds) },
                        )
                    }
                }
                items(visible, key = { it.id }) { track ->
                    // All rows remain lazy. Cover loads only for composed items.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                            .clickable(enabled = !selecting && state.playbackConnected) {
                                actions.onTrackClicked(track, visible)
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
    EnoCollapsingStrip(
        visible = stripVisible,
        title = stripTitle,
        blur = blur,
    ) {
        if (canPlayPage || canPlayAlbum) {
            EnoRoundButton(
                icon = EnoIcons.SHUFFLE,
                contentDescription = stringResource(R.string.library_shuffle_all),
                enabled = playEnabled,
                onClick = { if (canPlayAlbum) actions.onAlbumShuffleAll() else actions.onShuffleAll() },
            )
            EnoRoundButton(
                icon = EnoIcons.PLAY,
                contentDescription = stringResource(R.string.ui_play),
                accent = true,
                enabled = playEnabled,
                onClick = { if (canPlayAlbum) actions.onAlbumPlayAll() else actions.onPlayAll() },
            )
        }
        // Поиск не пропадает при прокрутке: наверх и раскрыть поле (уточнение владельца 2026-11-14).
        if (selectedGroupKey == null) {
            EnoRoundButton(
                icon = EnoIcons.SEARCH,
                contentDescription = stringResource(R.string.ui_search),
                filled = false,
                onClick = openSearch,
            )
        }
    }
    }
}

private fun sectionTitle(section: LibraryPage): Int = when (section) {
    LibraryPage.TRACKS -> R.string.ui_songs_title
    LibraryPage.ALBUMS -> R.string.library_tab_albums
    LibraryPage.ARTISTS -> R.string.library_tab_artists
    LibraryPage.ALBUM_ARTISTS -> R.string.library_tab_album_artists
}

private const val HEADER_KEY = "library-header"
private const val EMPTY_KEY = "library-empty"

/** Полоска появляется, когда крупный заголовок (≈ 56 dp) ушёл под системную строку. */
private val STRIP_AFTER = 56.dp

/** Поля шапки как в макетах: 28 dp от края (16 — у списка, ещё 12 — у шапки до D4). */
private val HEADER_EXTRA_PADDING = 12.dp

@Composable
private fun sectionCount(section: LibraryPage, count: Int): String {
    val formatted = remember(count) { NumberFormat.getIntegerInstance().format(count) }
    return pluralStringResource(
        when (section) {
            LibraryPage.TRACKS -> R.plurals.library_songs_count
            LibraryPage.ALBUMS -> R.plurals.library_albums_count
            LibraryPage.ARTISTS -> R.plurals.library_artists_count
            LibraryPage.ALBUM_ARTISTS -> R.plurals.library_album_artists_count
        },
        count,
        formatted,
    )
}

/** Раскрытое поле поиска на месте заголовка: пилюля `surface2` 48 dp, значок, крестик. */
@Composable
private fun LibrarySearchField(
    value: String,
    hint: String,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp).height(52.dp)
            .background(EnoColors.surface2, EnoRadii.full)
            .padding(start = 16.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(EnoIcons.SEARCH.outline),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = EnoColors.textSecondary,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = EnoColors.text),
            cursorBrush = SolidColor(EnoColors.accentText),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp).focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(hint, style = MaterialTheme.typography.bodyLarge, color = EnoColors.textSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            },
        )
        EnoRoundButton(
            icon = EnoIcons.CLOSE,
            contentDescription = stringResource(R.string.ui_search_close),
            filled = false,
            onClick = onClose,
        )
    }
}
