package app.enotone.android

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.enotone.android.core.ui.component.EnoBlurState
import app.enotone.library.LibraryPage
import app.enotone.library.LibraryPageVisibility
import app.enotone.library.OfflineTrackRow

/**
 * ViewModel медиатеки уровня Activity: общий для «Домой» (число песен, «Перемешать всё») и вкладок.
 * Ключ содержит только хеши, не сами данные входа; контейнер не пересоздаётся рекомпозицией.
 */
@Composable
internal fun rememberLibraryViewModel(accountEmail: String, appPassword: String): LibraryViewModel {
    val context = LocalContext.current.applicationContext
    val container = (context as EnoToneApplication).container
    val pages = remember(context) {
        LibraryPageVisibility.visible(LibraryPagePreferences(context).hiddenPages())
    }
    val factory = remember(accountEmail, appPassword) {
        LibraryViewModel.factory(
            email = accountEmail,
            password = appPassword,
            initialPage = pages.firstOrNull() ?: LibraryPage.TRACKS,
            container = container,
            unknownAlbumName = context.getString(R.string.library_unknown_album),
            messages = AlbumRefreshMessages(
                done = context.getString(R.string.album_refresh_done),
                retry = context.getString(R.string.album_refresh_retry_needed),
                backoff = context.getString(R.string.album_refresh_backoff),
                budget = context.getString(R.string.album_refresh_budget),
                failed = context.getString(R.string.album_refresh_failed),
            ),
            visiblePages = pages,
        )
    }
    return viewModel(
        key = "library:${accountEmail.hashCode()}:${appPassword.hashCode()}",
        factory = factory,
    )
}

/** Вкладка медиатеки в каркасе D3: страница выбирается вкладкой, видимость — из «Настроек». */
@Composable
internal fun OfflineLibraryScreen(
    model: LibraryViewModel,
    page: LibraryPage,
    visiblePages: List<LibraryPage>,
    accountEmail: String,
    appPassword: String,
    contentPadding: PaddingValues,
    scrollToTop: Int,
    blur: EnoBlurState? = null,
) {
    val context = LocalContext.current.applicationContext
    val container = (context as EnoToneApplication).container
    // Аудит п. 3: ViewModel живёт дольше экрана — страница и видимость передаются событиями.
    LaunchedEffect(model, visiblePages) { model.onVisiblePagesChanged(visiblePages) }
    LaunchedEffect(model, page) { model.onPageSelected(page) }
    val state by model.state.collectAsStateWithLifecycle()
    LibraryScreenContent(
        state = state,
        actions = model,
        artworkRepository = container.artworkPreviewRepository,
        accountEmail = accountEmail,
        appPassword = appPassword,
        contentPadding = contentPadding,
        scrollToTop = scrollToTop,
        blur = blur,
    )
}

/** Пустые действия для превью и снимков экранов: без binder, Room и WebDAV. */
// Preview dispatches all actions as no-ops; not production behavior.
@Suppress("TooManyFunctions")
internal object PreviewLibraryActions : LibraryScreenActions {
    override fun onSearch(query: String) = Unit
    override fun onPageSelected(page: LibraryPage) = Unit
    override fun onGroupSelected(key: String?) = Unit
    override fun onTrackClicked(track: OfflineTrackRow, context: List<OfflineTrackRow>) = Unit
    override fun onPlayAll() = Unit
    override fun onShuffleAll() = Unit
    override fun onAlbumPlayAll() = Unit
    override fun onAlbumShuffleAll() = Unit
    override fun onRequestAlbumRefresh(key: String, ids: List<String>) = Unit
    override fun onDismissAlbumRefresh() = Unit
    override fun onConfirmAlbumRefresh() = Unit
    override fun onPlayerDetails(visible: Boolean) = Unit
    override fun onPlayPause() = Unit
    override fun onNext() = Unit
    override fun onPrevious() = Unit
    override fun onRepeat() = Unit
    override fun onShuffle() = Unit
    override fun onSeekFraction(fraction: Float) = Unit
}

@Composable
private fun PreviewLibrary(state: LibraryUiState) {
    val context = LocalContext.current
    EnoToneTheme {
        LibraryScreenContent(
            state = state,
            actions = PreviewLibraryActions,
            artworkRepository = remember(context) { ArtworkPreviewRepository(context) },
            accountEmail = "",
            appPassword = "",
        )
    }
}

@Preview(name = "Медиатека: загрузка", showBackground = true)
@Composable
@Suppress("UnusedPrivateMember") // Invoked by Compose tooling, not from Kotlin.
private fun PreviewLibraryLoading() = PreviewLibrary(LibraryUiState(loading = true))

@Preview(name = "Медиатека: пусто", showBackground = true)
@Composable
@Suppress("UnusedPrivateMember") // Invoked by Compose tooling, not from Kotlin.
private fun PreviewLibraryEmpty() = PreviewLibrary(LibraryUiState(loading = false))

@Preview(name = "Медиатека: ошибка", showBackground = true)
@Composable
@Suppress("UnusedPrivateMember") // Invoked by Compose tooling, not from Kotlin.
private fun PreviewLibraryError() =
    PreviewLibrary(LibraryUiState(loading = false, failed = true))

@Preview(name = "Медиатека: данные", showBackground = true)
@Composable
@Suppress("UnusedPrivateMember") // Invoked by Compose tooling, not from Kotlin.
private fun PreviewLibraryData() {
    val row = OfflineTrackRow(id = "preview", title = "Sample track",
        artist = "Sample artist", album = "Sample album")
    PreviewLibrary(LibraryUiState(loading = false, tracks = listOf(row),
        visible = listOf(row)))
}
