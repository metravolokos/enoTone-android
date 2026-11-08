package app.enotone.android

import android.os.Bundle
import android.content.Context
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
import app.enotone.network.FlatLibraryWebDavClient
import app.enotone.network.FlatWebDavHttpError
import app.enotone.network.WebDavDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
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
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val importReport = stringResource(R.string.local_import_report)
    val storedReport = stringResource(R.string.local_saved_report)
    val localFailed = stringResource(R.string.local_store_failed)
    val missingCredentials = stringResource(R.string.missing_credentials)
    val diagnosticFailed = stringResource(R.string.diagnostic_failed)
    val countReport = stringResource(R.string.file_count_report)
    val countFailed = stringResource(R.string.file_count_failed)
    val httpFailure = stringResource(R.string.webdav_http_failed)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.diagnostic_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.diagnostic_explanation))
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.email)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(stringResource(R.string.app_password)) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
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
        if (report.isNotEmpty()) Text(report, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun DiagnosticScreenPreview() {
    MaterialTheme { DiagnosticScreen() }
}

private fun openLibraryDatabase(context: Context): EnoToneDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        EnoToneDatabase::class.java,
        "enotone-local-library.db",
    ).build()
