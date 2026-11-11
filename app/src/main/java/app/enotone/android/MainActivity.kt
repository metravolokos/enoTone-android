package app.enotone.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.enotone.database.EnoToneDatabase
import app.enotone.library.LibrarySnapshotStore
import app.enotone.library.LibraryPage
import app.enotone.library.LibraryPageVisibility
import app.enotone.library.LibraryTagStore
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.LibraryReindexCoordinator
import app.enotone.library.LibraryReindexScope
import app.enotone.library.AndroidAudioTagReader
import app.enotone.library.FastAudioTagReader
import app.enotone.library.ManagedIndexMode
import app.enotone.library.PilotTagIndexer
import app.enotone.library.PilotIndexProgress
import app.enotone.library.WebDavRangeReader
import app.enotone.library.WebDavRangeMediaDataSource
import app.enotone.database.RemoteTrackEntity
import app.enotone.playback.QueueStateFileStore
import java.text.Normalizer
import app.enotone.network.FlatLibraryWebDavClient
import app.enotone.network.FlatWebDavHttpError
import app.enotone.network.WebDavDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class CredentialVaultState { NONE, RESTORED, SAVED, DELETED, FAILED }
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent { EnoToneTheme { DiagnosticScreen() } }
    }
}

@Composable
private fun DiagnosticScreen() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var testFile by remember { mutableStateOf("") }
    var testInfinity by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pilotJob by remember { mutableStateOf<Job?>(null) }
    val managedStatus by ManagedScanRuntime.status.collectAsState()
    // Private per-track audit appears only on device, never in diagnostics.
    var tagAuditRows by remember { mutableStateOf<List<RemoteTrackEntity>>(emptyList()) }
    var tagAuditSelectedId by remember { mutableStateOf<String?>(null) }
    var tagAuditSearch by remember { mutableStateOf("") }
    var tagAuditMessage by remember { mutableStateOf("") }
    var confirmRefreshAll by remember { mutableStateOf(false) }
    var confirmLibraryReindex by remember { mutableStateOf(false) }
    var showingLibrary by remember { mutableStateOf(false) }
    var showingSettings by remember { mutableStateOf(false) }
    var initialLibraryPage by remember { mutableStateOf(LibraryPage.TRACKS) }
    // Daily listening is the default. Technical controls remain available
    // but are collapsed instead of occupying the full first screen.
    var showConnection by remember { mutableStateOf(true) }
    var showTechnicalTools by remember { mutableStateOf(false) }
    var showIndexDetails by remember { mutableStateOf(false) }
    var vaultState by remember { mutableStateOf(CredentialVaultState.NONE) }
    var vaultBusy by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val artworkPreferences = remember(appContext) { ArtworkPreferences(appContext) }
    var artworkMode by remember { mutableStateOf(artworkPreferences.mode()) }
    val artworkStatus by ArtworkScanRuntime.status.collectAsState()
    var artworkStorageBytes by remember { mutableStateOf<Long?>(null) }
    var artworkClearBusy by remember { mutableStateOf(false) }
    var artworkClearFeedback by remember { mutableStateOf("") }

    // Read only once per Activity creation. A missing entry does not create
    // any key; changes typed by the user during this read are not overwritten.
    LaunchedEffect(appContext) {
        try {
            val restored = withContext(Dispatchers.IO) {
                LocalCredentialVault.restore(appContext)
            }
            if (restored != null && email.isBlank() && password.isBlank()) {
                email = restored.email
                password = restored.password
                vaultState = CredentialVaultState.RESTORED
                showConnection = false
                // Restore the normal Home destination unless there is a
                // resumable offline queue: that case opens the existing library
                // so its mini-player is visible immediately, still paused.
                val resumeExists = withContext(Dispatchers.IO) {
                    runCatching {
                        val state = QueueStateFileStore(appContext).read()
                            ?: return@runCatching false
                        val sourceKey = LibrarySnapshotPlanner.accountKey(restored.email)
                        if (state.sourceKey != sourceKey) return@runCatching false
                        val ids = state.trackIds.toSet()
                        openLibraryDatabase(appContext).remoteTrackDao()
                            .allForSource(sourceKey).any {
                                it.id in ids && it.missingScans == 0 &&
                                    (it.remoteSize ?: 0L) > 0L
                            }
                    }.getOrDefault(false)
                }
                if (resumeExists) initialLibraryPage = LibraryPage.TRACKS
                showingLibrary = resumeExists
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            vaultState = CredentialVaultState.FAILED
        } finally {
            vaultBusy = false
        }
    }
    // User opt-in to automatic artwork preparation. On EACH foreground app
    // launch after credentials restore, resume only if all tag checkpoints for
    // the current snapshot are complete. Never autostart after device reboot.
    LaunchedEffect(artworkMode, vaultBusy, email, password, managedStatus.state, busy) {
        if (artworkMode != ArtworkLoadMode.AUTO_AFTER_IMPORT ||
            vaultBusy || busy || email.isBlank() || password.isBlank() ||
            managedStatus.state == ManagedScanState.RUNNING ||
            managedStatus.terminating ||
            ArtworkScanRuntime.status.value.state == ArtworkScanState.RUNNING ||
            ArtworkScanRuntime.status.value.terminating) return@LaunchedEffect
        try {
            val pendingTags = withContext(Dispatchers.IO) {
                val db = openLibraryDatabase(appContext)
                run {
                    db.remoteTrackDao().pendingFastScans(
                        LibrarySnapshotPlanner.accountKey(email)).isNotEmpty()
                }
            }
            if (pendingTags) return@LaunchedEffect
            if (!ArtworkIndexPlanner.needsPreparation(appContext, email)) return@LaunchedEffect
            if (ArtworkScanRuntime.prepare(email, password)) {
                try {
                    appContext.startForegroundService(
                        Intent(appContext, ArtworkIndexingForegroundService::class.java)
                            .setAction(ArtworkIndexingForegroundService.ACTION_START)
                    )
                } catch (_: RuntimeException) {
                    ArtworkScanRuntime.update(ArtworkScanState.ERROR, 0, 0)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A damaged Room/Keystore connection never blocks regular listening.
        }
    }

    LaunchedEffect(showingSettings, artworkStatus.state) {
        if (showingSettings) {
            artworkStorageBytes = withContext(Dispatchers.IO) {
                ArtworkPreviewRepository(appContext).storedBytes()
            }
        }
    }

    val artworkClearedMessage = stringResource(R.string.artwork_cache_cleared)
    val artworkClearFailedMessage = stringResource(R.string.artwork_cache_clear_failed)
    val importReport = stringResource(R.string.local_import_report)
    val storedReport = stringResource(R.string.local_saved_report)
    val storedTagsReport = stringResource(R.string.local_tag_coverage_report)
    val localFailed = stringResource(R.string.local_store_failed)
    val tagReport = stringResource(R.string.one_tag_report)
    val tagFailed = stringResource(R.string.one_tag_failed)
    val tagFailedDetail = stringResource(R.string.one_tag_failed_detail)
    val tagRangeMismatch = stringResource(R.string.one_tag_range_mismatch)
    val tagMissing = stringResource(R.string.one_tag_missing)
    val fastTagReport = stringResource(R.string.fast_tag_report)
    val pilotReport = stringResource(R.string.pilot_report)
    val pilotFailureDetail = stringResource(R.string.pilot_failure_detail)
    val pilotOffline = stringResource(R.string.pilot_offline_report)
    val pilotStopped = stringResource(R.string.pilot_stopped)
    val pilotFailed = stringResource(R.string.pilot_failed)
    val auditNoSearch = stringResource(R.string.tag_audit_no_search)
    val auditNoMissing = stringResource(R.string.tag_audit_no_missing)
    val auditSelectOne = stringResource(R.string.tag_audit_select_one)
    val auditFailed = stringResource(R.string.tag_audit_failed)
    val auditQueued = stringResource(R.string.tag_audit_queued)
    val auditQueuedAll = stringResource(R.string.tag_audit_all_queued)
    val managedPaused = stringResource(R.string.managed_index_paused)
    val managedStopped = stringResource(R.string.managed_index_stopped)
    val managedError = stringResource(R.string.managed_index_failed)
    val missingCredentials = stringResource(R.string.missing_credentials)
    val diagnosticFailed = stringResource(R.string.diagnostic_failed)
    val countReport = stringResource(R.string.file_count_report)
    val countFailed = stringResource(R.string.file_count_failed)
    val httpFailure = stringResource(R.string.webdav_http_failed)

    fun showPilot(progress: PilotIndexProgress): String =
        String.format(Locale.getDefault(), pilotReport,
            progress.requested, progress.attempted, progress.savedWithTags,
            progress.completedWithoutTags, progress.retryableErrors,
            progress.staleWrites, progress.getRequests, progress.downloadedBytes,
            progress.remaining, progress.stopCode, progress.permanentErrors) +
            if (progress.lastErrorStage == "NONE") "" else
                "\n" + String.format(Locale.getDefault(), pilotFailureDetail,
                    progress.lastErrorStage, progress.lastErrorReason,
                    if (progress.lastErrorHttpStatus == 0) "—" else progress.lastErrorHttpStatus.toString())

    val runPilot: (Int) -> Unit = pilot@{ count ->
        if (managedStatus.state == ManagedScanState.RUNNING) return@pilot
        busy = true
        report = ""
        pilotJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val database = openLibraryDatabase(appContext)
                    run {
                        PilotTagIndexer(database).run(
                            email, password, count,
                            onProgress = { progress ->
                                withContext(Dispatchers.Main) { report = showPilot(progress) }
                            },
                        )
                    }
                }
                report = showPilot(result)
            } catch (_: CancellationException) {
                report = pilotStopped
            } catch (_: Exception) {
                report = pilotFailed
            } finally {
                pilotJob = null
                busy = false
            }
        }
    }

    // Notification controls are part of the manual background-indexing UX.
    // Android 13+ asks for that permission only after the user presses Start.
    var notificationPermissionDenied by remember { mutableStateOf(false) }
    var requestedContinuousIndex by remember { mutableStateOf(false) }
    val launchManagedForegroundService: (Boolean) -> Unit = { continuous ->
        val mode = if (continuous) ManagedIndexMode.CONTINUOUS else ManagedIndexMode.BOUNDED
        if (!busy && managedStatus.state != ManagedScanState.RUNNING &&
            !managedStatus.terminating && email.isNotBlank() && password.isNotBlank() &&
            ManagedScanRuntime.prepare(email, password, mode)) {
            try {
                appContext.startForegroundService(
                    Intent(appContext, IndexingForegroundService::class.java)
                        .setAction(IndexingForegroundService.ACTION_START)
                )
            } catch (_: RuntimeException) {
                ManagedScanRuntime.update(ManagedScanState.ERROR)
            }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionDenied = !granted
        if (granted) launchManagedForegroundService(requestedContinuousIndex)
    }
    val startManagedSession: (Boolean) -> Unit = { continuous ->
        requestedContinuousIndex = continuous
        if (Build.VERSION.SDK_INT >= 33 &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationPermissionDenied = false
            launchManagedForegroundService(continuous)
        }
    }

    BackHandler(enabled = showingLibrary || showingSettings) {
        if (showingLibrary) showingLibrary = false else showingSettings = false
    }
    if (showingLibrary) {
        OfflineLibraryScreen(
            accountEmail = email, appPassword = password,
            initialPage = initialLibraryPage,
            onBack = { showingLibrary = false },
        )
        return
    }

    // The previous initial diagnostic screen is now a separate Settings route.
    // Read the existing preferences; no Room query or WebDAV request is needed.
    if (!showingSettings) {
        val pages = LibraryPageVisibility.visible(
            LibraryPagePreferences(appContext).hiddenPages())
        EnoToneHomeScreen(
            visiblePages = pages,
            connectedAccount = email.isNotBlank(),
            onSettings = { showingSettings = true },
            onOpenPage = { page ->
                initialLibraryPage = page
                showingLibrary = true
            },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
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
        AppearanceSettings()
        LibraryVisibilitySettings()
        ArtworkSettingsPanel(
            mode = artworkMode,
            onModeChange = { selected ->
                if (selected != artworkMode) {
                    try {
                        artworkPreferences.setMode(selected)
                        artworkMode = selected
                        if (selected == ArtworkLoadMode.ON_DEMAND &&
                            ArtworkScanRuntime.status.value.state == ArtworkScanState.RUNNING) {
                            appContext.startService(
                                Intent(appContext, ArtworkIndexingForegroundService::class.java)
                                    .setAction(ArtworkIndexingForegroundService.ACTION_STOP)
                            )
                        }
                    } catch (_: Exception) {
                        artworkClearFeedback = localFailed
                    }
                }
            },
            status = artworkStatus,
            storedBytes = artworkStorageBytes,
            clearBusy = artworkClearBusy,
            clearFeedback = artworkClearFeedback,
            onClear = {
                artworkClearBusy = true
                artworkClearFeedback = ""
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            ArtworkPreviewRepository(appContext).clearAll()
                        }
                        artworkStorageBytes = 0L
                        artworkClearFeedback = artworkClearedMessage
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        artworkClearFeedback = artworkClearFailedMessage
                    } finally {
                        artworkClearBusy = false
                    }
                }
            },
            onPause = {
                appContext.startService(
                    Intent(appContext, ArtworkIndexingForegroundService::class.java)
                        .setAction(ArtworkIndexingForegroundService.ACTION_PAUSE)
                )
            },
            onStop = {
                appContext.startService(
                    Intent(appContext, ArtworkIndexingForegroundService::class.java)
                        .setAction(ArtworkIndexingForegroundService.ACTION_STOP)
                )
            },
        )
        OutlinedButton(onClick = { showConnection = !showConnection }) {
            Text(stringResource(R.string.home_connection_settings))
        }
        if (showConnection || email.isBlank() || password.isBlank()) {
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.email)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(stringResource(R.string.app_password)) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        // Saving is explicit and optional. Editing fields alone never writes
        // secrets to disk; the vault is separate from Room and from Media3.
        Text(stringResource(R.string.credential_vault_explanation),
            style = MaterialTheme.typography.bodySmall)
        // Long RU/EN labels must remain usable on narrow Huawei screens.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.fillMaxWidth(),
                enabled = !vaultBusy && !busy &&
                email.isNotBlank() && password.isNotBlank(),
                onClick = {
                    vaultBusy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                LocalCredentialVault.save(appContext, email, password)
                            }
                            vaultState = CredentialVaultState.SAVED
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            vaultState = CredentialVaultState.FAILED
                        } finally {
                            vaultBusy = false
                        }
                    }
                }) { Text(stringResource(R.string.credential_vault_save)) }
            Button(modifier = Modifier.fillMaxWidth(),
                enabled = !vaultBusy && !busy, onClick = {
                vaultBusy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            LocalCredentialVault.clear(appContext)
                        }
                        email = ""
                        password = ""
                        vaultState = CredentialVaultState.DELETED
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        vaultState = CredentialVaultState.FAILED
                    } finally {
                        vaultBusy = false
                    }
                }
            }) { Text(stringResource(R.string.credential_vault_clear)) }
        }
        when (vaultState) {
            CredentialVaultState.RESTORED ->
                Text(stringResource(R.string.credential_vault_restored))
            CredentialVaultState.SAVED ->
                Text(stringResource(R.string.credential_vault_saved))
            CredentialVaultState.DELETED ->
                Text(stringResource(R.string.credential_vault_deleted))
            CredentialVaultState.FAILED ->
                Text(stringResource(R.string.credential_vault_failed))
            CredentialVaultState.NONE -> Unit
        }
        }
        OutlinedButton(onClick = { showTechnicalTools = !showTechnicalTools }) {
            Text(stringResource(if (showTechnicalTools)
                R.string.home_hide_technical else R.string.home_show_technical))
        }
        if (showTechnicalTools) {
        // Playback-specific numeric telemetry has moved out of Now Playing.
        PlaybackDiagnosticsPanel()
        Text(stringResource(R.string.home_diagnostics_title),
            style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = testFile, onValueChange = { testFile = it },
            label = { Text(stringResource(R.string.test_file)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            Checkbox(checked = testInfinity, onCheckedChange = { testInfinity = it })
            Text(stringResource(R.string.test_infinity))
        }
        Button(enabled = !busy, onClick = {
            if (email.isBlank() || password.isBlank()) {
                report = missingCredentials
            } else {
                busy = true
                report = ""
                scope.launch {
                    try {
                        report = withContext(Dispatchers.IO) {
                            WebDavDiagnostics().inspect(email, password, testFile, testInfinity).toSanitizedText()
                        }
                    } catch (_: Exception) {
                        report = diagnosticFailed
                    } finally {
                        busy = false
                    }
                }
            }
        }) {
            Text(if (busy) stringResource(R.string.running) else stringResource(R.string.run_probe))
        }
        Button(enabled = !busy && email.isNotBlank() && password.isNotBlank(), onClick = {
            busy = true
            report = ""
            scope.launch {
                try {
                    val counts = withContext(Dispatchers.IO) {
                        FlatLibraryWebDavClient().use { client ->
                            val listing = client.list(email, password)
                            intArrayOf(
                                listing.tracks.size, listing.skippedDirectories,
                                listing.skippedNested, listing.skippedNonAudio, listing.invalidEntries,
                            )
                        }
                    }
                    // Only counters cross into UI. Never display or log private remote paths.
                    report = String.format(
                        Locale.getDefault(), countReport,
                        counts[0], counts[1], counts[2], counts[3], counts[4],
                    )
                } catch (e: FlatWebDavHttpError) {
                    report = String.format(Locale.getDefault(), httpFailure, e.statusCode)
                } catch (_: Exception) {
                    report = countFailed
                } finally {
                    busy = false
                }
            }
        }) {
            Text(stringResource(R.string.count_audio_files))
        }
        }
        Text(stringResource(R.string.home_library_maintenance),
            style = MaterialTheme.typography.titleMedium)
        // One explicit action: quick listing + background metadata completion.
        // Existing current-version checkpoints are skipped, not re-downloaded.
        Button(enabled = !busy &&
            managedStatus.state != ManagedScanState.RUNNING &&
            !managedStatus.terminating &&
            email.isNotBlank() && password.isNotBlank(), onClick = {
            busy = true
            report = ""
            scope.launch {
                var startMetadata = false
                try {
                    val result = withContext(Dispatchers.IO) {
                        FlatLibraryWebDavClient().use { client ->
                            val listing = client.list(email, password)
                            val database = openLibraryDatabase(appContext)
                            run {
                                val store = LibrarySnapshotStore(database)
                                val plan = store.save(email, listing)
                                val pending = database.remoteTrackDao().pendingFastScans(
                                    LibrarySnapshotPlanner.accountKey(email)).size
                                intArrayOf(listing.tracks.size, store.presentCount(email),
                                    plan.inserted, plan.changed, plan.unchanged, pending)
                            }
                        }
                    }
                    report = String.format(Locale.getDefault(), importReport,
                        result[0], result[1], result[2], result[3], result[4])
                    startMetadata = result[5] > 0
                } catch (e: FlatWebDavHttpError) {
                    report = String.format(Locale.getDefault(), httpFailure, e.statusCode)
                } catch (_: Exception) {
                    report = localFailed
                } finally {
                    busy = false
                }
                // Start only after the Room listing is committed and the
                // Activity's busy gate has been released. The foreground
                // service owns resume, limits, cancellation and notifications.
                if (startMetadata) startManagedSession(true)
            }
        }) {
            Text(stringResource(R.string.save_local_library))
        }
        // Explicit full reindex is a DIFFERENT action from normal incremental
        // import/update. Queue tags once, invalidate their lazy artwork refs,
        // then start the already-existing resumable foreground reader.
        val reindexQueuedFormat = stringResource(R.string.library_reindex_queued)
        val reindexEmpty = stringResource(R.string.library_reindex_empty)
        val reindexFailed = stringResource(R.string.library_reindex_failed)
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && email.isNotBlank() && password.isNotBlank() &&
                managedStatus.state != ManagedScanState.RUNNING &&
                !managedStatus.terminating,
            onClick = { confirmLibraryReindex = true },
        ) {
            Text(stringResource(R.string.library_reindex_action))
        }
        if (confirmLibraryReindex) {
            AlertDialog(
                onDismissRequest = { confirmLibraryReindex = false },
                title = { Text(stringResource(R.string.library_reindex_confirm_title)) },
                text = { Text(stringResource(R.string.library_reindex_confirm_message)) },
                confirmButton = {
                    TextButton(
                        enabled = !busy && email.isNotBlank() && password.isNotBlank() &&
                            managedStatus.state != ManagedScanState.RUNNING &&
                            !managedStatus.terminating,
                        onClick = {
                            confirmLibraryReindex = false
                            busy = true
                            report = ""
                            scope.launch {
                                var queued = 0
                                try {
                                    queued = withContext(Dispatchers.IO) {
                                        val db = openLibraryDatabase(appContext)
                                        run {
                                            val selected = LibraryReindexCoordinator(db).queue(
                                                email, LibraryReindexScope.EntireLibrary)
                                            if (selected.queued > 0) {
                                                val targets = selected.artworkSources.map { item ->
                                                    PlayingArtworkTarget(
                                                        id = item.id,
                                                        encodedPath = item.encodedPath,
                                                        remoteSize = item.remoteSize,
                                                        remoteEtag = item.remoteEtag,
                                                    )
                                                }
                                                // Missing cache files are harmless; metadata
                                                // checkpoints must remain durable.
                                                try {
                                                    ArtworkPreviewRepository(appContext)
                                                        .invalidate(email, targets)
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (_: Exception) {
                                                    // Tag queue is already committed. A
                                                    // damaged optional image cache must
                                                    // never prevent the indexer starting.
                                                }
                                            }
                                            selected.queued
                                        }
                                    }
                                    report = if (queued > 0) String.format(
                                        Locale.getDefault(), reindexQueuedFormat, queued)
                                    else reindexEmpty
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    report = reindexFailed
                                } finally {
                                    busy = false
                                }
                                // The existing service owns the bounded batches,
                                // notifications, Pause, Stop and saved checkpoints.
                                if (queued > 0) startManagedSession(true)
                            }
                        },
                    ) { Text(stringResource(R.string.library_reindex_confirm_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmLibraryReindex = false }) {
                        Text(stringResource(R.string.album_refresh_cancel))
                    }
                },
            )
        }
        if (showTechnicalTools) {
        Button(enabled = !busy && email.isNotBlank(), onClick = {
            busy = true
            report = ""
            scope.launch {
                try {
                    val saved = withContext(Dispatchers.IO) {
                        val database = openLibraryDatabase(appContext)
                        run {
                            LibrarySnapshotStore(database).presentCount(email)
                        }
                    }
                    report = String.format(Locale.getDefault(), storedReport, saved)
                } catch (_: Exception) {
                    report = localFailed
                } finally {
                    busy = false
                }
            }
        }) {
            Text(stringResource(R.string.read_local_library))
        }
        Button(enabled = !busy && email.isNotBlank(), onClick = {
            busy = true
            report = ""
            scope.launch {
                try {
                    val coverage = withContext(Dispatchers.IO) {
                        val database = openLibraryDatabase(appContext)
                        run {
                            LibraryTagStore(database).storedCoverage(email)
                        }
                    }
                    // Only aggregate counts reach the UI, never private metadata.
                    report = String.format(
                        Locale.getDefault(), storedTagsReport,
                        coverage.presentTracks,
                        coverage.tracksWithAnyTag,
                        coverage.tracksWithTitle,
                        coverage.tracksWithArtist,
                        coverage.tracksWithAlbum,
                        coverage.tracksWithAlbumArtist,
                    )
                } catch (_: Exception) {
                    report = localFailed
                } finally {
                    busy = false
                }
            }
        }) {
            Text(stringResource(R.string.read_stored_tags_offline))
        }
        Text(stringResource(R.string.tag_audit_title),
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.tag_audit_explanation),
            style = MaterialTheme.typography.bodySmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.fillMaxWidth(),
                enabled = !busy && email.isNotBlank(), onClick = {
                busy = true
                tagAuditMessage = ""
                scope.launch {
                    try {
                        tagAuditRows = withContext(Dispatchers.IO) {
                            val database = openLibraryDatabase(appContext)
                            run { LibraryTagStore(database).missingAlbum(email) }
                        }
                        tagAuditSelectedId = null
                        tagAuditMessage = if (tagAuditRows.isEmpty())
                            auditNoMissing else auditSelectOne
                    } catch (_: Exception) {
                        tagAuditMessage = auditFailed
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.tag_audit_missing_album)) }
            Button(modifier = Modifier.fillMaxWidth(),
                enabled = !busy && email.isNotBlank(), onClick = {
                busy = true
                tagAuditMessage = ""
                scope.launch {
                    try {
                        tagAuditRows = withContext(Dispatchers.IO) {
                            val database = openLibraryDatabase(appContext)
                            run { LibraryTagStore(database).missingAlbumArtist(email) }
                        }
                        tagAuditSelectedId = null
                        tagAuditMessage = if (tagAuditRows.isEmpty())
                            auditNoMissing else auditSelectOne
                    } catch (_: Exception) {
                        tagAuditMessage = auditFailed
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.tag_audit_missing_album_artist)) }
        }
        OutlinedTextField(
            value = tagAuditSearch,
            onValueChange = { tagAuditSearch = it },
            label = { Text(stringResource(R.string.tag_audit_search_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && email.isNotBlank() && tagAuditSearch.isNotBlank(),
            onClick = {
                busy = true
                tagAuditMessage = ""
                scope.launch {
                    try {
                        tagAuditRows = withContext(Dispatchers.IO) {
                            val database = openLibraryDatabase(appContext)
                            run {
                                LibraryTagStore(database).findTracks(
                                    email, tagAuditSearch)
                            }
                        }
                        tagAuditSelectedId = null
                        tagAuditMessage = if (tagAuditRows.isEmpty())
                            auditNoSearch else auditSelectOne
                    } catch (_: Exception) {
                        tagAuditMessage = auditFailed
                    } finally { busy = false }
                }
            },
        ) { Text(stringResource(R.string.tag_audit_search_action)) }
        if (tagAuditRows.isNotEmpty()) {
            Text(stringResource(R.string.tag_audit_first_rows, tagAuditRows.size),
                style = MaterialTheme.typography.bodySmall)
            tagAuditRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.fileName, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { tagAuditSelectedId = row.id }) {
                        Text(stringResource(if (tagAuditSelectedId == row.id)
                            R.string.tag_audit_selected else R.string.tag_audit_select))
                    }
                }
            }
        }
        tagAuditRows.firstOrNull { it.id == tagAuditSelectedId }?.let { selected ->
            Text(stringResource(R.string.tag_audit_selected_details,
                selected.title ?: "—", selected.artist ?: "—",
                selected.album ?: "—", selected.albumArtist ?: "—",
                selected.tagScanCode ?: "—"),
                style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = !busy && email.isNotBlank() &&
                    managedStatus.state != ManagedScanState.RUNNING &&
                    !managedStatus.terminating,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val queued = withContext(Dispatchers.IO) {
                                val database = openLibraryDatabase(appContext)
                                run {
                                    LibraryTagStore(database).queueOneForRefresh(
                                        email, selected.id)
                                }
                            }
                            tagAuditMessage = if (queued) auditQueued else auditFailed
                        } catch (_: Exception) {
                            tagAuditMessage = auditFailed
                        } finally { busy = false }
                    }
                },
            ) { Text(stringResource(R.string.tag_audit_queue_selected)) }
        }
        if (tagAuditMessage.isNotEmpty()) {
            Text(tagAuditMessage, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = confirmRefreshAll,
                onCheckedChange = { confirmRefreshAll = it })
            Text(stringResource(R.string.tag_audit_refresh_all_confirm),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall)
        }
        Button(
            enabled = !busy && confirmRefreshAll && email.isNotBlank() &&
                managedStatus.state != ManagedScanState.RUNNING &&
                !managedStatus.terminating,
            onClick = {
                busy = true
                scope.launch {
                    try {
                        val queued = withContext(Dispatchers.IO) {
                            val database = openLibraryDatabase(appContext)
                            run { LibraryTagStore(database).queueAllForRefresh(email) }
                        }
                        tagAuditMessage = String.format(Locale.getDefault(),
                            auditQueuedAll, queued)
                        confirmRefreshAll = false
                    } catch (_: Exception) {
                        tagAuditMessage = auditFailed
                    } finally { busy = false }
                }
            },
        ) { Text(stringResource(R.string.tag_audit_queue_all)) }
        Text(stringResource(R.string.tag_audit_queue_instructions),
            style = MaterialTheme.typography.bodySmall)

        Button(enabled = !busy && email.isNotBlank() && password.isNotBlank(), onClick = {
            busy = true
            report = ""
            scope.launch {
                var tagStage = "LOCAL_DB"
                var rangeReader: WebDavRangeReader? = null
                try {
                    val result = withContext(Dispatchers.IO) {
                        val database = openLibraryDatabase(appContext)
                        val selected = run {
                            val sourceKey = LibrarySnapshotPlanner.accountKey(email)
                            val available = database.remoteTrackDao().allForSource(sourceKey)
                                .filter { it.missingScans == 0 && (it.remoteSize ?: 0L) > 0L }
                            if (testFile.isBlank()) {
                                // Exactly ONE track (MP3 preferred), never a bulk scan.
                                available.sortedWith(
                                    compareBy<RemoteTrackEntity> { !it.fileName.endsWith(".mp3", true) }
                                        .thenBy { it.id }
                                ).firstOrNull()
                            } else {
                                val path = testFile.trim()
                                val name = path.removePrefix("/AllMusic/")
                                if (!path.startsWith("/AllMusic/") || '/' in name || name.isBlank()) null
                                else available.singleOrNull {
                                    it.fileName == Normalizer.normalize(name, Normalizer.Form.NFC)
                                }
                            }
                        }
                        if (selected == null) {
                            intArrayOf(-1, 0, 0, 0, 0, 0, 0)
                        } else {
                            tagStage = "SOURCE"
                            val reader = WebDavRangeReader(
                                encodedPath = selected.encodedPath,
                                fileSize = selected.remoteSize!!,
                                email = email,
                                appPassword = password,
                                etag = selected.remoteEtag,
                            )
                            rangeReader = reader
                            val source = WebDavRangeMediaDataSource(reader)
                            try {
                                // Separate an HTTP/Range failure from a platform extractor failure.
                                // Exactly one 64-KiB block at most; reused by the retriever.
                                tagStage = "RANGE"
                                if (source.readAt(0, ByteArray(1), 0, 1) != 1) {
                                    throw java.io.IOException("Range probe unavailable")
                                }
                                tagStage = "EXTRACTOR"
                                val tags = AndroidAudioTagReader().read(source, selected.fileName)
                                val stats = reader.stats()
                                // Persist only if Room still contains the same file version.
                                tagStage = "STORE"
                                val writeDatabase = openLibraryDatabase(appContext)
                                val persisted = run {
                                    LibraryTagStore(writeDatabase).saveIfUnchanged(
                                        email, selected, tags)
                                }
                                intArrayOf(1,
                                    if (tags.title != null) 1 else 0,
                                    if (tags.artist != null) 1 else 0,
                                    if (tags.album != null) 1 else 0,
                                    stats.requests, stats.downloadedBytes.toInt(),
                                    if (persisted) 1 else 0,
                                    stats.cacheRevisitHits, stats.cacheEvictions)
                            } finally {
                                source.close()
                            }
                        }
                    }
                    report = if (result[0] < 0) tagMissing else String.format(
                        Locale.getDefault(), tagReport,
                        result[1], result[2], result[3], result[4], result[5], result[6],
                        result[7], result[8])
                } catch (_: Exception) {
                    val stats = rangeReader?.stats()
                    val reason = stats?.issue?.name
                        ?: when (tagStage) {
                            "EXTRACTOR" -> "PLATFORM_EXTRACTOR"
                            "STORE" -> "LOCAL_DB_WRITE"
                            else -> "PREPARATION"
                        }
                    report = String.format(
                        Locale.getDefault(), tagFailedDetail, tagStage, reason,
                        stats?.httpStatus?.toString() ?: "—",
                        stats?.requests ?: 0, stats?.downloadedBytes ?: 0L,
                    )
                    // Numbers only: never put Content-Range raw header, path or ETag in UI.
                    stats?.rangeMismatch?.let { mismatch ->
                        report += " " + String.format(
                            Locale.getDefault(), tagRangeMismatch,
                            mismatch.kind.name,
                            mismatch.expectedStart.toString(),
                            mismatch.expectedEnd.toString(),
                            mismatch.expectedTotal.toString(),
                            mismatch.receivedStart?.toString() ?: "—",
                            mismatch.receivedEnd?.toString() ?: "—",
                            mismatch.receivedTotal?.toString() ?: "—",
                        )
                    }
                } finally {
                    busy = false
                }
            }
        }) {
            Text(stringResource(R.string.probe_one_audio_tag))
        }
        Button(enabled = !busy && email.isNotBlank() && password.isNotBlank(), onClick = {
            busy = true
            report = ""
            scope.launch {
                var stage = "LOCAL_DB"
                var activeReader: WebDavRangeReader? = null
                try {
                    val outcome = withContext(Dispatchers.IO) {
                        val database = openLibraryDatabase(appContext)
                        val selected = run {
                            val key = LibrarySnapshotPlanner.accountKey(email)
                            val candidates = database.remoteTrackDao().allForSource(key).filter {
                                it.missingScans == 0 &&
                                    (it.remoteSize ?: 0L) > 0L &&
                                    (it.fileName.endsWith(".mp3", true) ||
                                     it.fileName.endsWith(".flac", true))
                            }
                            if (testFile.isBlank()) {
                                // Same stable MP3-first order as the older diagnostic.
                                candidates.sortedWith(
                                    compareBy<RemoteTrackEntity> {
                                        !it.fileName.endsWith(".mp3", true)
                                    }.thenBy { it.id }
                                ).firstOrNull()
                            } else {
                                val path = testFile.trim()
                                val name = path.removePrefix("/AllMusic/")
                                if (!path.startsWith("/AllMusic/") ||
                                    name.isBlank() || '/' in name) null
                                else candidates.singleOrNull {
                                    it.fileName == Normalizer.normalize(name, Normalizer.Form.NFC)
                                }
                            }
                        }
                        if (selected == null) null else {
                            stage = "FAST_RANGE"
                            val reader = WebDavRangeReader(
                                encodedPath = selected.encodedPath,
                                fileSize = selected.remoteSize!!,
                                email = email, appPassword = password,
                                etag = selected.remoteEtag,
                            )
                            activeReader = reader
                            reader.use {
                                // No MediaMetadataRetriever fallback: this button verifies
                                // bounded ID3/FLAC metadata only, never audio frames.
                                val parsed = FastAudioTagReader().read(
                                    selected.fileName, reader.fileSize, reader::readAt)
                                val stats = reader.stats()
                                var persisted = false
                                val extractedTags = parsed.tags
                                if (extractedTags != null) {
                                    stage = "FAST_STORE"
                                    val storeDatabase = openLibraryDatabase(appContext)
                                    persisted = run {
                                        LibraryTagStore(storeDatabase).saveIfUnchanged(
                                            email, selected, extractedTags)
                                    }
                                }
                                FastProbeResult(
                                    parsed.code.name,
                                    if (parsed.embeddedTitle) 1 else 0,
                                    if (parsed.embeddedArtist) 1 else 0,
                                    if (parsed.embeddedAlbum) 1 else 0,
                                    stats.requests, stats.downloadedBytes,
                                    if (persisted) 1 else 0,
                                )
                            }
                        }
                    }
                    report = if (outcome == null) tagMissing else
                        String.format(Locale.getDefault(), fastTagReport,
                            outcome.code, outcome.title, outcome.artist,
                            outcome.album, outcome.requests, outcome.bytes,
                            outcome.saved)
                } catch (_: Exception) {
                    val stats = activeReader?.stats()
                    val reason = stats?.issue?.name ?: "FAST_PROBE_ERROR"
                    report = String.format(Locale.getDefault(), tagFailedDetail,
                        stage, reason, stats?.httpStatus?.toString() ?: "—",
                        stats?.requests ?: 0, stats?.downloadedBytes ?: 0L)
                } finally {
                    busy = false
                }
            }
        }) {
            Text(stringResource(R.string.fast_probe_one_track))
        }
        Button(enabled = !busy && email.isNotBlank(), onClick = {
            busy = true
            report = ""
            scope.launch {
                try {
                    val stored = withContext(Dispatchers.IO) {
                        val database = openLibraryDatabase(appContext)
                        run {
                            PilotTagIndexer(database).offlineCheckpoint(email)
                        }
                    }
                    report = String.format(Locale.getDefault(), pilotOffline,
                        stored.successful, stored.retryableErrors,
                        stored.permanentErrors, stored.notAttempted)
                } catch (_: Exception) {
                    report = pilotFailed
                } finally {
                    busy = false
                }
            }
        }) {
            Text(stringResource(R.string.pilot_check_offline))
        }
        Button(enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            onClick = { runPilot(10) }) {
            Text(stringResource(R.string.pilot_ten))
        }
        Button(enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            onClick = { runPilot(20) }) {
            Text(stringResource(R.string.pilot_twenty))
        }
        // Explicit, bounded foreground-only resume: ≤100 candidates,
        // ≤60 GET, ≤4 MiB, 180-second soft wall-clock cap per press.
        // Checkpoints already committed in Room are never re-downloaded.
        Button(enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = { runPilot(100) }) {
            Text(stringResource(R.string.pilot_resume_bounded))
        }
        Text(stringResource(R.string.pilot_resume_explanation),
            style = MaterialTheme.typography.bodySmall)
        Button(enabled = busy && pilotJob != null, onClick = {
            pilotJob?.cancel()
        }) {
            Text(stringResource(R.string.pilot_cancel))
        }

        }
        Text(stringResource(R.string.managed_index_title),
            style = MaterialTheme.typography.titleMedium)
        if (showTechnicalTools) {
            Text(stringResource(R.string.managed_index_explanation),
                style = MaterialTheme.typography.bodySmall)
        }
        if (notificationPermissionDenied) {
            Text(stringResource(R.string.managed_notification_permission_denied),
                style = MaterialTheme.typography.bodySmall)
        }
        if (showTechnicalTools) {
        Button(modifier = Modifier.fillMaxWidth(),
            enabled = !busy && managedStatus.state != ManagedScanState.RUNNING &&
                !managedStatus.terminating &&
                email.isNotBlank() && password.isNotBlank(),
            onClick = { startManagedSession(false) }) {
            Text(stringResource(if (managedStatus.state in setOf(ManagedScanState.PAUSED,
                    ManagedScanState.STOPPED, ManagedScanState.COMPLETE,
                    ManagedScanState.ERROR))
                R.string.managed_index_resume else R.string.managed_index_start))
        }
        }
        if (showTechnicalTools) {
            Button(modifier = Modifier.fillMaxWidth(),
                enabled = !busy && managedStatus.state != ManagedScanState.RUNNING &&
                    !managedStatus.terminating &&
                    email.isNotBlank() && password.isNotBlank(),
                onClick = { startManagedSession(true) }) {
                Text(stringResource(R.string.managed_index_all))
            }
            Text(stringResource(R.string.managed_index_all_explanation),
                style = MaterialTheme.typography.bodySmall)
        }
        if (managedStatus.state == ManagedScanState.RUNNING) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = managedStatus.state == ManagedScanState.RUNNING,
                onClick = {
                    appContext.startService(
                        Intent(appContext, IndexingForegroundService::class.java)
                            .setAction(IndexingForegroundService.ACTION_PAUSE)
                    )
                }) { Text(stringResource(R.string.managed_index_pause)) }
            Button(enabled = managedStatus.state == ManagedScanState.RUNNING,
                onClick = {
                    appContext.startService(
                        Intent(appContext, IndexingForegroundService::class.java)
                            .setAction(IndexingForegroundService.ACTION_STOP)
                    )
                }) { Text(stringResource(R.string.managed_index_stop)) }
        }
        }
        managedStatus.progress?.let { current ->
            Text(stringResource(R.string.home_index_summary,
                current.savedWithTags, current.remaining))
            OutlinedButton(onClick = { showIndexDetails = !showIndexDetails }) {
                Text(stringResource(if (showIndexDetails)
                    R.string.home_hide_index_details else R.string.home_show_index_details))
            }
            if (showIndexDetails || showTechnicalTools) {
            Text(stringResource(R.string.managed_index_report,
                current.completedBatches, current.attempted, current.savedWithTags,
                current.retryableErrors, current.permanentErrors, current.getRequests,
                current.downloadedBytes, current.remaining, current.stopCode),
                style = MaterialTheme.typography.bodyMedium)
            if (current.lastErrorStage != "NONE" &&
                managedStatus.failureStage == null) {
                Text(stringResource(R.string.managed_index_last_error,
                    current.lastErrorStage, current.lastErrorReason,
                    current.lastErrorHttpStatus),
                    style = MaterialTheme.typography.bodySmall)
            }
            }
        }
        if (showIndexDetails || showTechnicalTools) {
        managedStatus.failureStage?.let { stage ->
            Text(stringResource(R.string.managed_index_internal_failure,
                stage, managedStatus.failureReason ?: "UNEXPECTED"),
                style = MaterialTheme.typography.bodySmall)
        }
        }
        val managedMessage = when (managedStatus.state) {
            ManagedScanState.PAUSED -> managedPaused
            ManagedScanState.STOPPED -> managedStopped
            ManagedScanState.ERROR -> managedError
            else -> ""
        }
        if (managedMessage.isNotEmpty()) {
            Text(managedMessage, style = MaterialTheme.typography.bodySmall)
        }
        if (report.isNotEmpty()) Text(report, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun DiagnosticScreenPreview() {
    EnoToneTheme { DiagnosticScreen() }
}

/**
 * Returns the process-wide shared database (M6-0/2). Never close the result.
 * The name is kept for now; call sites move to repositories in M6-0/4.
 */
internal fun openLibraryDatabase(context: Context): EnoToneDatabase =
    EnoToneDatabase.getInstance(context)

/** Aggregates only; never carries remote names or tag values to Compose. */
private data class FastProbeResult(
    val code: String,
    val title: Int,
    val artist: Int,
    val album: Int,
    val requests: Int,
    val bytes: Long,
    val saved: Int,
)
