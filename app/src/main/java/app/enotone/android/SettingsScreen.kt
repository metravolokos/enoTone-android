package app.enotone.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.enotone.library.LibraryPage

@Composable
internal fun DiagnosticScreen() {
    val appContext = LocalContext.current.applicationContext
    val container = (appContext as EnoToneApplication).container
    val factory = remember(container) { SettingsViewModelFactory(container) }
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
    var showingLibrary by remember { mutableStateOf(false) }
    var showingSettings by remember { mutableStateOf(false) }
    var initialLibraryPage by remember { mutableStateOf(LibraryPage.TRACKS) }
    var showConnection by remember { mutableStateOf(true) }
    var showIndexDetails by remember { mutableStateOf(false) }
    var showTechnicalTools by remember { mutableStateOf(false) }
    val playerModel: PlayerViewModel = viewModel(factory = remember(appContext) {
        PlayerViewModel.factory(container)
    })
    val player by playerModel.state.collectAsStateWithLifecycle()
    val libraryStateHolder = rememberSaveableStateHolder()
    SettingsAccountEffects(account, showingLibrary, showingSettings, container, playerModel, artworkModel,
        onConnected = { showConnection = false }, onSignedOut = { showingLibrary = false; showConnection = true })
    SettingsNotificationPermission(if (indexing.permissionPending) indexing.permissionRequest else 0,
        indexingModel::onNotificationPermission)
    BackHandler(enabled = player.nowPlaying || showingLibrary || showingSettings) {
        when {
            player.nowPlaying -> playerModel.onNowPlaying(false)
            showingLibrary -> showingLibrary = false
            showingSettings -> showingSettings = false
        }
    }
    if (SettingsMusicDestination(player, playerModel, account, showingLibrary, showingSettings,
        initialLibraryPage, libraryStateHolder, appContext, onLibraryBack = { showingLibrary = false },
        onSettings = { showingSettings = true }, onOpenPage = { page ->
            initialLibraryPage = page
            showingLibrary = true
        })) return

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp)
            .padding(bottom = if (player.playback.queueTotal > 0) 88.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { showingSettings = false }) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_back),
                    contentDescription = stringResource(R.string.library_back),
                )
            }
            Text(stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge)
        }
        // Version first: owner reports from Huawei must name the exact build.
        val version = remember(appContext) { appVersionName(appContext) }
        if (version.isNotBlank()) {
            Text(stringResource(R.string.settings_app_version, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppearanceSettings()
        LibraryVisibilitySettings()
        SettingsArtworkSection(artwork, artworkModel::changeMode, artworkModel::clear,
            artworkModel::pause, artworkModel::stop)
        OutlinedButton(onClick = { showConnection = !showConnection }) {
            Text(stringResource(R.string.home_connection_settings))
        }
        AccountSettingsSection(
            account = account,
            showFields = showConnection || !account.loggedIn,
            onEmailChange = accountModel::onEmailChanged,
            onPasswordChange = accountModel::onPasswordChanged,
            onVerify = accountModel::verifyConnection,
            onRequestSignOut = accountModel::requestSignOut,
            onConfirmSignOut = accountModel::confirmSignOut,
            onCancelSignOut = accountModel::cancelSignOut,
        )
        OutlinedButton(onClick = { showTechnicalTools = !showTechnicalTools }) {
            Text(stringResource(if (showTechnicalTools)
                R.string.home_hide_technical else R.string.home_show_technical))
        }
        if (showTechnicalTools) {
            PlaybackDiagnosticsPanel()
            SettingsDiagnosticsSection(diagnostics, diagnosticsModel)
        }
        SettingsIndexingSection(indexing, indexingModel)
        if (showTechnicalTools) {
            SettingsAuditSection(diagnostics, diagnosticsModel)
            SettingsTagToolsSection(diagnostics, diagnosticsModel)
        }
        SettingsIndexingStatusSection(indexing, indexingModel, showTechnicalTools,
            showIndexDetails) { showIndexDetails = !showIndexDetails }
        if (indexing.report.isNotEmpty()) Text(indexing.report, style = MaterialTheme.typography.bodyMedium)
    }
    Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        .navigationBarsPadding()) {
        EnoToneMiniPlayer(
            player = player,
            onOpen = { playerModel.onNowPlaying(true) },
            onPlayPause = playerModel::onPlayPause,
            onNext = playerModel::onNext,
        )
    }
    }
}

@Composable
private fun SettingsAccountEffects(
    account: AccountUiState,
    showingLibrary: Boolean,
    showingSettings: Boolean,
    container: AppContainer,
    playerModel: PlayerViewModel,
    artworkModel: ArtworkSettingsViewModel,
    onConnected: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val email = account.email
    val password = account.password
    LaunchedEffect(account.loggedIn, account.busy, email, password) {
        container.settingsWorkSession.updateLogin(
            if (account.loggedIn && !account.busy) StoredWebDavLogin(email, password) else null)
    }
    LaunchedEffect(showingSettings) { artworkModel.setVisible(showingSettings) }
    LaunchedEffect(showingLibrary, account.loggedIn, email, password) {
        if (showingLibrary && account.loggedIn) playerModel.onCredentialsPersisted(email, password)
    }
    LaunchedEffect(account.verifiedVersion) {
        if (account.verifiedVersion > 0) {
            playerModel.onCredentialsPersisted(email, password)
            onConnected()
        }
    }
    LaunchedEffect(account.signedOutVersion) {
        if (account.signedOutVersion > 0) {
            onSignedOut()
        }
    }
    LaunchedEffect(account.loggedIn) { if (account.loggedIn) onConnected() }
}
