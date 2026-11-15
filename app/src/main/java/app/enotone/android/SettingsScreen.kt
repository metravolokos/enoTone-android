package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.enotone.library.LibraryPageVisibility
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun DiagnosticScreen(openNowPlayingRequest: Int = 0) {
    val appContext = LocalContext.current.applicationContext
    val container = (appContext as EnoToneApplication).container
    val sessionModel: SettingsSessionViewModel = viewModel(factory = SettingsSessionFactory)
    val session = sessionModel.session
    val factory = remember(container, session) { SettingsViewModelFactory(container, session) }
    val accountModel: AccountSettingsViewModel = viewModel(factory = remember(appContext) {
        AccountSettingsViewModel.factory(container, appContext)
    })
    val account by accountModel.state.collectAsStateWithLifecycle()
    val indexingModel: LibraryIndexingViewModel = viewModel(factory = factory)
    val artworkModel: ArtworkSettingsViewModel = viewModel(factory = factory)
    val diagnosticsModel: DiagnosticsViewModel = viewModel(factory = factory)
    val indexing by indexingModel.state.collectAsStateWithLifecycle()
    val artwork by artworkModel.state.collectAsStateWithLifecycle()
    val diagnostics by diagnosticsModel.state.collectAsStateWithLifecycle()
    // Навигация каркаса D3 (ADR-0086): переживает поворот и пересоздание Activity.
    var nav by rememberSaveable(stateSaver = navSaver) { mutableStateOf(AppNavState()) }
    val playerModel: PlayerViewModel = viewModel(factory = remember(appContext) {
        PlayerViewModel.factory(container)
    })
    val player by playerModel.state.collectAsStateWithLifecycle()
    val libraryStateHolder = rememberSaveableStateHolder()
    val inSettings = nav.overlay != null
    // Видимость страниц перечитывается при выходе из «Настроек», где её меняют (ADR-0052).
    val pages = remember(inSettings) {
        LibraryPageVisibility.visible(LibraryPagePreferences(appContext).hiddenPages())
    }
    LaunchedEffect(pages) { nav = AppNavigator.onVisiblePages(nav, pages) }
    SettingsAccountEffects(account, showingMusic = !inSettings, showingSettings = inSettings, session,
        playerModel, artworkModel, onSignedOut = { nav = AppNavigator.onSignedOut(nav) })
    SettingsNotificationPermission(if (indexing.permissionPending) indexing.permissionRequest else 0,
        indexingModel::onNotificationPermission)
    // Нажатие на уведомление: «Сейчас играет» поверх любого экрана; «Назад» вернёт на прежний.
    LaunchedEffect(openNowPlayingRequest) {
        if (openNowPlayingRequest > 0) playerModel.onNowPlaying(true)
    }
    val expanded = player.nowPlaying && player.playback.queueTotal > 0
    val searchRegistry = remember { AppSearchBackRegistry() }
    val activeSearchBack = searchRegistry.handler(appRouteKey(nav))
    val backTarget = appBackTarget(expanded, nav, activeSearchBack != null)
    var backProgress by remember { mutableFloatStateOf(0f) }
    var previewTarget by remember { mutableStateOf<AppBackTarget?>(null) }
    var previewRoute by remember { mutableStateOf<AppRouteKey?>(null) }
    var playerDragProgress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val resetJob = remember { arrayOfNulls<Job>(1) }
    LaunchedEffect(expanded) { if (!expanded) playerDragProgress = 0f }
    AppBackGesture(
        enabled = backTarget != AppBackTarget.EXIT,
        onPreview = { progress ->
            resetJob[0]?.cancel()
            if (previewTarget == null) {
                previewTarget = backTarget
                previewRoute = appRouteKey(nav)
            }
            backProgress = progress
        },
        onCommit = {
            dispatchAppBack(
                target = backTarget,
                closePlayer = { playerModel.onNowPlaying(false) },
                navigateBack = { AppNavigator.back(nav)?.let { nav = it } },
                closeSearch = { activeSearchBack?.invoke() },
            )
            // Уходящий экран сохраняет preview transform до завершения 250-мс exit;
            // новый маршрут масштабируется отдельно и остаётся в масштабе 1.
            resetJob[0] = scope.launch {
                delay(AppMotionSpec.PAGE_CLOSE_MS.toLong())
                backProgress = 0f
                previewTarget = null
                previewRoute = null
            }
        },
        onCancel = {
            resetJob[0]?.cancel()
            backProgress = 0f
            previewTarget = null
            previewRoute = null
        },
    )
    // Музыка остаётся под полным плеером: shared element не может перелететь
    // из Composable, который был удалён из дерева (как было в D3).
    AppMotionRoot(
        inSettings = inSettings,
        diagnostics = nav.overlay == AppOverlay.DIAGNOSTICS,
        playerExpanded = expanded,
        backProgress = if (!expanded && previewTarget == AppBackTarget.OVERLAY) backProgress else 0f,
        music = {
            AppMusicShell(
                nav = nav, pages = pages, account = account, player = player, playerModel = playerModel,
                libraryStateHolder = libraryStateHolder,
                onSelectTab = { tab -> nav = AppNavigator.select(nav, tab) },
                onOpenDetail = { detail -> nav = AppNavigator.push(nav, detail) },
                onBackDetail = { nav = AppNavigator.pop(nav) },
                onSettings = { nav = AppNavigator.openSettings(nav) },
                playerExpanded = expanded && backProgress <= 0f && playerDragProgress <= 0f,
                backProgress = if (!expanded && previewTarget in setOf(
                        AppBackTarget.DETAIL, AppBackTarget.TAB,
                    )) backProgress else 0f,
                backPreviewRoute = previewRoute,
                searchRegistry = searchRegistry,
            )
        },
        player = {
            AppNowPlaying(
                player, playerModel,
                predictiveBackProgress = if (previewTarget == AppBackTarget.PLAYER) backProgress else 0f,
                onDragProgress = { playerDragProgress = it },
            )
        },
        settings = { showingDiagnostics ->
            // Мини-плеера в настройках и диагностике нет (O-10).
            key(showingDiagnostics) {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showingDiagnostics) {
            SettingsHeader(stringResource(R.string.diagnostics_title), "") { nav = AppNavigator.back(nav) ?: nav }
            SettingsDiagnosticsContent(diagnostics, indexing.status, diagnosticsModel)
        } else {
            val version = remember(appContext) { appVersionName(appContext) }
            SettingsHeader(stringResource(R.string.settings_title), version) {
                nav = AppNavigator.back(nav) ?: nav
            }
            SettingsGroup(stringResource(R.string.settings_group_cloud)) {
                AccountSettingsSection(
                    account = account,
                    onEmailChange = accountModel::onEmailChanged,
                    onPasswordChange = accountModel::onPasswordChanged,
                    onVerify = accountModel::verifyConnection,
                    onRequestSignOut = accountModel::requestSignOut,
                    onConfirmSignOut = accountModel::confirmSignOut,
                    onCancelSignOut = accountModel::cancelSignOut,
                )
            }
            SettingsGroup(stringResource(R.string.settings_group_library)) {
                SettingsIndexingSection(indexing, indexingModel)
            }
            SettingsGroup { LibraryVisibilitySettings() }
            SettingsGroup { AppearanceSettings() }
            SettingsGroup {
                SettingsArtworkSection(artwork, artworkModel::changeMode, artworkModel::clear,
                    artworkModel::pause, artworkModel::stop)
            }
            SettingsDiagnosticsEntry { nav = AppNavigator.openDiagnostics(nav) }
        }
            }
            }
        },
    )
}

@Composable
private fun SettingsAccountEffects(
    account: AccountUiState,
    showingMusic: Boolean,
    showingSettings: Boolean,
    session: SettingsWorkSession,
    playerModel: PlayerViewModel,
    artworkModel: ArtworkSettingsViewModel,
    onSignedOut: () -> Unit,
) {
    // Рабочий вход — только подтверждённый (ADR-0080): поля формы сюда не попадают.
    val working = account.workingLogin
    val library = account.libraryLogin
    LaunchedEffect(working) { session.updateLogin(working) }
    LaunchedEffect(showingSettings) { artworkModel.setVisible(showingSettings) }
    LaunchedEffect(showingMusic, library) {
        // «Домой» тоже запускает воспроизведение («Перемешать всё»), поэтому — любой экран музыки.
        if (showingMusic && library != null) playerModel.onCredentialsPersisted(library.email, library.password)
    }
    LaunchedEffect(account.verifiedVersion) {
        val verified = account.active
        if (account.verifiedVersion > 0 && verified != null) {
            playerModel.onCredentialsPersisted(verified.email, verified.password)
        }
    }
    LaunchedEffect(account.signedOutVersion) {
        if (account.signedOutVersion > 0) {
            onSignedOut()
        }
    }
}

@Composable
private fun SettingsHeader(title: String, version: String, onBack: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_back),
                    contentDescription = stringResource(R.string.library_back),
                )
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        // Version first: owner reports from Huawei must name the exact build.
        if (version.isNotBlank()) {
            Text(stringResource(R.string.settings_app_version, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
}

/** Вход в подэкран «Диагностика»: технические сведения убраны с основного экрана. */
@Composable
private fun SettingsDiagnosticsEntry(onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.diagnostics_entry_summary), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val navSaver = Saver<AppNavState, String>(
    save = { AppNavigator.save(it) },
    restore = { AppNavigator.restore(it) },
)
