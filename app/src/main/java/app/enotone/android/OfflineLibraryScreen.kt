package app.enotone.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.enotone.library.LibraryPage
import app.enotone.library.LibraryPageVisibility
import app.enotone.library.OfflineTrackRow

/** A thin screen entry point; the application container is never recreated by recomposition. */
@Composable
internal fun OfflineLibraryScreen(
    accountEmail: String,
    appPassword: String,
    initialPage: LibraryPage = LibraryPage.TRACKS,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val container = (context as EnoToneApplication).container
    val pages = remember(context) {
        LibraryPageVisibility.visible(LibraryPagePreferences(context).hiddenPages())
    }
    val factory = remember(accountEmail, appPassword, initialPage, pages) {
        LibraryViewModel.factory(
            email = accountEmail,
            password = appPassword,
            initialPage = initialPage,
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
    // A key only contains hashes, not the credential itself.
    val model: LibraryViewModel = viewModel(
        key = "library:${accountEmail.hashCode()}:${appPassword.hashCode()}",
        factory = factory,
    )
    val state by model.state.collectAsStateWithLifecycle()
    LibraryScreenContent(
        state = state,
        actions = model,
        artworkRepository = container.artworkPreviewRepository,
        accountEmail = accountEmail,
        appPassword = appPassword,
        onBack = onBack,
    )
}

/** Preview-only no-op actions. There is no binder, Room or WebDAV in previews. */
private object PreviewLibraryActions : LibraryScreenActions {
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
            onBack = {},
        )
    }
}

@Preview(name = "Медиатека: загрузка", showBackground = true)
@Composable private fun PreviewLibraryLoading() = PreviewLibrary(LibraryUiState(loading = true))

@Preview(name = "Медиатека: пусто", showBackground = true)
@Composable private fun PreviewLibraryEmpty() = PreviewLibrary(LibraryUiState(loading = false))

@Preview(name = "Медиатека: ошибка", showBackground = true)
@Composable private fun PreviewLibraryError() =
    PreviewLibrary(LibraryUiState(loading = false, failed = true))

@Preview(name = "Медиатека: данные", showBackground = true)
@Composable private fun PreviewLibraryData() {
    val row = OfflineTrackRow(id = "preview", title = "Sample track",
        artist = "Sample artist", album = "Sample album")
    PreviewLibrary(LibraryUiState(loading = false, tracks = listOf(row),
        visible = listOf(row)))
}
