package app.enotone.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
internal fun FirstRunRoute(
    account: AccountUiState,
    accountModel: AccountSettingsViewModel,
    indexing: LibraryIndexingUiState,
    indexingModel: LibraryIndexingViewModel,
    preferences: FirstRunPreferences,
    signOutPending: Boolean,
    libraryState: LibraryUiState?,
    hasValidatedInternet: Boolean,
    appContent: @Composable () -> Unit,
) {
    var firstRunCompleted by remember(preferences) { mutableStateOf(preferences.firstRunCompleted()) }
    val plan = rememberFirstRunPlan(
        account, indexing, preferences, signOutPending, libraryState, hasValidatedInternet, firstRunCompleted,
    ) { firstRunCompleted = it }
    val songCount = indexing.importedTrackCount ?: libraryState?.takeIf { !it.loading }?.tracks?.size
    val visibleSongCount = songCount ?: 0
    var firstRunError by remember { mutableStateOf<FirstRunError?>(null) }
    var indexingErrorFromImport by remember { mutableStateOf(false) }

    FirstRunImportEffect(account, indexing, indexingModel, plan, hasValidatedInternet)
    FirstRunAccountErrorEffect(account.error, plan?.phase, firstRunError) { error ->
        firstRunError = error
    }
    FirstRunImportErrorEffect(indexing, plan?.phase, firstRunError) { error ->
        firstRunError = error
        indexingErrorFromImport = true
    }
    FirstRunManagedErrorEffect(indexing, plan?.phase, firstRunError) { error ->
        firstRunError = error
        indexingErrorFromImport = false
    }
    LaunchedEffect(plan?.phase) {
        if (plan?.phase == FirstRunPhase.APP) firstRunError = null
    }

    when {
        plan == null -> FirstRunRestoring()
        plan.phase == FirstRunPhase.APP -> appContent()
        else -> FirstRunExperience(
            phase = plan.phase,
            account = account,
            indexing = indexing,
            songCount = visibleSongCount,
            albumCount = libraryState?.takeIf { !it.loading }?.groupIndex?.albums?.size ?: 0,
            artistCount = libraryState?.takeIf { !it.loading }?.groupIndex?.artists?.size ?: 0,
            error = firstRunError,
            onEmailChange = accountModel::onEmailChanged,
            onPasswordChange = accountModel::onPasswordChanged,
            onConnect = accountModel::verifyConnection,
            onListenWhileReading = { completeFirstRun(preferences) { firstRunCompleted = it } },
            onGoToMusic = { completeFirstRun(preferences) { firstRunCompleted = it } },
            onErrorDismiss = {
                if (plan.phase == FirstRunPhase.LOGIN) accountModel.dismissError()
                firstRunError = null
            },
            onErrorAction = { error ->
                if (plan.phase == FirstRunPhase.LOGIN) accountModel.dismissError()
                firstRunError = null
                handleFirstRunError(error, plan.phase, indexingErrorFromImport, accountModel, indexingModel)
            },
        )
    }
}

@Composable
private fun FirstRunRestoring() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}

@Composable
private fun rememberFirstRunPlan(
    account: AccountUiState,
    indexing: LibraryIndexingUiState,
    preferences: FirstRunPreferences,
    signOutPending: Boolean,
    libraryState: LibraryUiState?,
    hasValidatedInternet: Boolean,
    firstRunCompleted: Boolean?,
    onCompletedChange: (Boolean?) -> Unit,
): FirstRunPlan? {
    val effectiveFirstRunCompleted = if (account.verifiedVersion > 0) {
        preferences.firstRunCompleted()
    } else {
        firstRunCompleted
    }
    val songCount = indexing.importedTrackCount ?: libraryState?.takeIf { !it.loading }?.tracks?.size
    val plan = if (account.restoring) null else FirstRunPhasePlanner.plan(
        FirstRunPlanningState(
            signOutPending = signOutPending,
            hasLogin = account.libraryLogin != null,
            firstRunCompleted = effectiveFirstRunCompleted,
            restoreCompletedWithoutCredentials = account.restoreCompletedWithoutCredentials,
            indexingActive = indexing.status.active,
            indexingComplete = indexing.importVersion > 0 &&
                indexing.status.state == ManagedScanState.COMPLETE,
            libraryImportFinished = indexing.importVersion > 0,
            songCount = songCount,
            pendingTagCount = indexing.pendingTagCount,
            hasValidatedInternet = hasValidatedInternet,
        ),
    )
    FirstRunPlanPersistenceEffect(plan, preferences, onCompletedChange)
    FirstRunVerifiedAccountEffect(account.verifiedVersion, preferences, onCompletedChange)
    return plan
}
