package app.enotone.android

import android.os.Bundle
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.Room
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.enotone.database.EnoToneDatabase
import app.enotone.library.LibrarySnapshotStore
import app.enotone.library.LibraryTagStore
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.AndroidAudioTagReader
import app.enotone.library.FastAudioTagReader
import app.enotone.library.PilotTagIndexer
import app.enotone.library.PilotIndexProgress
import app.enotone.library.ManagedTagIndexSession
import app.enotone.library.ManagedIndexProgress
import app.enotone.library.WebDavRangeReader
import app.enotone.library.WebDavRangeMediaDataSource
import app.enotone.database.RemoteTrackEntity
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
private enum class ManagedScanState { IDLE, RUNNING, PAUSED, STOPPED, COMPLETE, ERROR }

/** Fail closed if Wi-Fi is absent. Mobile/VPN requires explicit per-run opt-in. */
internal fun managedIndexNetworkAllowed(context: Context, allowMobile: Boolean): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val active = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(active) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        (allowMobile || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
}

class MainActivity : ComponentActivity() {
    // Only metadata scans are paused on background; Media3 playback is untouched.
    internal var pauseManagedIndexOnStop: (() -> Unit)? = null
    override fun onStop() {
        pauseManagedIndexOnStop?.invoke()
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { DiagnosticScreen() }
        }
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
    var managedJob by remember { mutableStateOf<Job?>(null) }
    var managedState by remember { mutableStateOf(ManagedScanState.IDLE) }
    var managedReport by remember { mutableStateOf("") }
    var allowMobileIndexing by remember { mutableStateOf(false) }
    var showingLibrary by remember { mutableStateOf(false) }
    var vaultState by remember { mutableStateOf(CredentialVaultState.NONE) }
    var vaultBusy by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val currentContext = LocalContext.current
    val appContext = currentContext.applicationContext
    val ownerActivity = currentContext as? MainActivity

    DisposableEffect(ownerActivity) {
        // Android/Huawei background restrictions: never keep scanning unseen.
        ownerActivity?.pauseManagedIndexOnStop = {
            if (managedJob?.isActive == true) {
                managedState = ManagedScanState.PAUSED
                managedJob?.cancel()
            }
        }
        onDispose {
            ownerActivity?.pauseManagedIndexOnStop = null
            managedJob?.cancel()
        }
    }

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
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            vaultState = CredentialVaultState.FAILED
        } finally {
            vaultBusy = false
        }
    }
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
    val managedFormat = stringResource(R.string.managed_index_report)
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

    val runPilot: (Int) -> Unit = { count ->
        busy = true
        report = ""
        pilotJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val database = openLibraryDatabase(appContext)
                    try {
                        PilotTagIndexer(database).run(
                            email, password, count,
                            onProgress = { progress ->
                                withContext(Dispatchers.Main) { report = showPilot(progress) }
                            },
                        )
                    } finally {
                        database.close()
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

    val startManagedSession: () -> Unit = {
        if (!busy && email.isNotBlank() && password.isNotBlank()) {
            busy = true
            managedState = ManagedScanState.RUNNING
            managedReport = ""
            val activeEmail = email
            val activePassword = password
            val permitMobile = allowMobileIndexing
            managedJob = scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val database = openLibraryDatabase(appContext)
                        try {
                            ManagedTagIndexSession(database).run(
                                activeEmail, activePassword,
                                networkAllowed = {
                                    managedIndexNetworkAllowed(appContext, permitMobile)
                                },
                                onProgress = { current ->
                                    withContext(Dispatchers.Main) {
                                        managedReport = String.format(Locale.getDefault(),
                                            managedFormat, current.completedBatches,
                                            current.attempted, current.savedWithTags,
                                            current.retryableErrors, current.permanentErrors,
                                            current.getRequests, current.downloadedBytes,
                                            current.remaining, current.stopCode)
                                    }
                                },
                            )
                        } finally {
                            database.close()
                        }
                    }
                    managedReport = String.format(Locale.getDefault(), managedFormat,
                        result.completedBatches, result.attempted, result.savedWithTags,
                        result.retryableErrors, result.permanentErrors, result.getRequests,
                        result.downloadedBytes, result.remaining, result.stopCode)
                    managedState = ManagedScanState.COMPLETE
                } catch (cancelled: CancellationException) {
                    managedReport += if (managedState == ManagedScanState.STOPPED)
                        "\n" + managedStopped else "\n" + managedPaused
                    if (managedState != ManagedScanState.STOPPED)
                        managedState = ManagedScanState.PAUSED
                    throw cancelled
                } catch (_: Exception) {
                    managedState = ManagedScanState.ERROR
                    managedReport += "\n" + managedError
                } finally {
                    managedJob = null
                    busy = false
                }
            }
        }
    }

    if (showingLibrary) {
        OfflineLibraryScreen(accountEmail = email, appPassword = password,
            onBack = { showingLibrary = false })
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.diagnostic_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.diagnostic_explanation))
        Button(enabled = !busy && email.isNotBlank(), onClick = {
            showingLibrary = true
        }) { Text(stringResource(R.string.library_open)) }
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
        Button(enabled = !busy && email.isNotBlank() && password.isNotBlank(), onClick = {
            busy = true
            report = ""
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        FlatLibraryWebDavClient().use { client ->
                            val listing = client.list(email, password)
                            val database = openLibraryDatabase(appContext)
                            try {
                                val store = LibrarySnapshotStore(database)
                                val plan = store.save(email, listing)
                                intArrayOf(listing.tracks.size, store.presentCount(email),
                                    plan.inserted, plan.changed, plan.unchanged)
                            } finally {
                                database.close()
                            }
                        }
                    }
                    report = String.format(Locale.getDefault(), importReport,
                        result[0], result[1], result[2], result[3], result[4])
                } catch (e: FlatWebDavHttpError) {
                    report = String.format(Locale.getDefault(), httpFailure, e.statusCode)
                } catch (_: Exception) {
                    report = localFailed
                } finally {
                    busy = false
                }
            }
        }) {
            Text(stringResource(R.string.save_local_library))
        }
        Button(enabled = !busy && email.isNotBlank(), onClick = {
            busy = true
            report = ""
            scope.launch {
                try {
                    val saved = withContext(Dispatchers.IO) {
                        val database = openLibraryDatabase(appContext)
                        try {
                            LibrarySnapshotStore(database).presentCount(email)
                        } finally {
                            database.close()
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
                        try {
                            LibraryTagStore(database).storedCoverage(email)
                        } finally {
                            database.close()
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
        Button(enabled = !busy && email.isNotBlank() && password.isNotBlank(), onClick = {
            busy = true
            report = ""
            scope.launch {
                var tagStage = "LOCAL_DB"
                var rangeReader: WebDavRangeReader? = null
                try {
                    val result = withContext(Dispatchers.IO) {
                        val database = openLibraryDatabase(appContext)
                        val selected = try {
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
                        } finally {
                            database.close()
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
                                val persisted = try {
                                    LibraryTagStore(writeDatabase).saveIfUnchanged(
                                        email, selected, tags)
                                } finally {
                                    writeDatabase.close()
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
                        val selected = try {
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
                        } finally {
                            database.close()
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
                                    persisted = try {
                                        LibraryTagStore(storeDatabase).saveIfUnchanged(
                                            email, selected, extractedTags)
                                    } finally {
                                        storeDatabase.close()
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
                        try {
                            PilotTagIndexer(database).offlineCheckpoint(email)
                        } finally {
                            database.close()
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

        Text(stringResource(R.string.managed_index_title),
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.managed_index_explanation),
            style = MaterialTheme.typography.bodySmall)
        Row {
            Checkbox(checked = allowMobileIndexing, enabled = !busy,
                onCheckedChange = { allowMobileIndexing = it })
            Text(stringResource(R.string.managed_index_allow_mobile),
                style = MaterialTheme.typography.bodySmall)
        }
        Button(modifier = Modifier.fillMaxWidth(),
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            onClick = { startManagedSession() }) {
            Text(stringResource(if (managedState == ManagedScanState.PAUSED)
                R.string.managed_index_resume else R.string.managed_index_start))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = managedState == ManagedScanState.RUNNING &&
                managedJob?.isActive == true, onClick = {
                    managedState = ManagedScanState.PAUSED
                    managedJob?.cancel()
                }) { Text(stringResource(R.string.managed_index_pause)) }
            Button(enabled = managedState == ManagedScanState.RUNNING ||
                managedState == ManagedScanState.PAUSED, onClick = {
                    managedState = ManagedScanState.STOPPED
                    managedJob?.cancel()
                }) { Text(stringResource(R.string.managed_index_stop)) }
        }
        if (managedReport.isNotEmpty())
            Text(managedReport, style = MaterialTheme.typography.bodyMedium)
        if (report.isNotEmpty()) Text(report, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun DiagnosticScreenPreview() {
    MaterialTheme { DiagnosticScreen() }
}

internal fun openLibraryDatabase(context: Context): EnoToneDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        EnoToneDatabase::class.java,
        "enotone-local-library.db",
    ).build()

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
