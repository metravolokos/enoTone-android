package app.enotone.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun FirstRunPlanPersistenceEffect(
    plan: FirstRunPlan?,
    preferences: FirstRunPreferences,
    onCompletedChange: (Boolean) -> Unit,
) {
    LaunchedEffect(plan?.writeFirstRunCompleted) {
        plan?.writeFirstRunCompleted?.let { value ->
            preferences.setFirstRunCompleted(value)
            onCompletedChange(value)
        }
    }
}

@Composable
internal fun FirstRunVerifiedAccountEffect(
    verifiedVersion: Int,
    preferences: FirstRunPreferences,
    onCompletedChange: (Boolean?) -> Unit,
) {
    LaunchedEffect(verifiedVersion) {
        if (verifiedVersion > 0) onCompletedChange(preferences.firstRunCompleted())
    }
}

@Composable
internal fun FirstRunImportEffect(
    account: AccountUiState,
    indexing: LibraryIndexingUiState,
    indexingModel: LibraryIndexingViewModel,
    plan: FirstRunPlan?,
    hasValidatedInternet: Boolean,
    onAutomaticRetry: () -> Unit,
) {
    var started by remember(account.libraryLogin?.email) { mutableStateOf(false) }
    var awaitingNetworkRecovery by remember(account.libraryLogin?.email) {
        mutableStateOf(!hasValidatedInternet)
    }
    LaunchedEffect(
        plan?.phase,
        account.libraryLogin?.email,
        indexing.hasLogin,
        indexing.busy,
        indexing.status.active,
        indexing.permissionPending,
        indexing.importVersion,
        indexing.pendingTagCount,
        indexing.status.state,
        indexing.status.terminating,
        indexing.operationFailureVersion,
        indexingModel.scanIsTerminating,
        hasValidatedInternet,
    ) {
        if (plan?.phase !in setOf(FirstRunPhase.INDEXING, FirstRunPhase.INDEXING_OFFLINE)) {
            return@LaunchedEffect
        }
        // A budget pause is not a finished library and the screen has no resume action: clear the
// one-shot guard so the effect continues from the checkpoint. Only a SESSION_ pause resumes —
// a user pressing Pause keeps its code "PAUSED" and must stay sticky. Requiring progress in
// the paused session (attempted > 0) stops a limit hit at zero work from looping.
        if (indexing.status.state == ManagedScanState.PAUSED && !indexing.status.terminating) {
            val stopCode = indexing.status.progress?.stopCode.orEmpty()
            val advanced = (indexing.status.progress?.attempted ?: 0) > 0
            if (stopCode.startsWith(SESSION_LIMIT_PREFIX) && advanced) started = false
        }
        if (!hasValidatedInternet) {
            awaitingNetworkRecovery = true
            return@LaunchedEffect
        }
        if (awaitingNetworkRecovery) {
            if (canRetryFirstRunImport(account, indexing) && indexingModel.retryFirstRun()) {
                awaitingNetworkRecovery = false
                started = true
                onAutomaticRetry()
            }
            return@LaunchedEffect
        }
        if (shouldStartFirstRunImport(account, indexing, plan, hasValidatedInternet, started)) {
            if (indexingModel.retryFirstRun()) started = true
        }
    }
}

private fun shouldStartFirstRunImport(
    account: AccountUiState,
    indexing: LibraryIndexingUiState,
    plan: FirstRunPlan?,
    hasValidatedInternet: Boolean,
    alreadyStarted: Boolean,
): Boolean {
    val isIndexing = plan?.phase in setOf(FirstRunPhase.INDEXING, FirstRunPhase.INDEXING_OFFLINE)
    return isIndexing && account.libraryLogin != null && indexing.hasLogin &&
        !indexing.busy && !indexing.status.active && !indexing.permissionPending &&
        hasValidatedInternet && !alreadyStarted && canAutomaticallyRetryFirstRunImport(indexing)
}

private fun canRetryFirstRunImport(
    account: AccountUiState,
    indexing: LibraryIndexingUiState,
): Boolean = account.libraryLogin != null && indexing.hasLogin && !indexing.busy &&
    !indexing.status.active && !indexing.status.terminating && !indexing.permissionPending &&
    canAutomaticallyRetryFirstRunImport(indexing)

internal fun canAutomaticallyRetryFirstRunImport(indexing: LibraryIndexingUiState): Boolean =
    when (indexing.status.state) {
        ManagedScanState.IDLE -> indexing.operationFailure == null ||
            indexing.operationFailure == FirstRunIndexingFailure.CLOUD_UNAVAILABLE
        ManagedScanState.ERROR -> indexing.operationFailure?.let {
            it == FirstRunIndexingFailure.CLOUD_UNAVAILABLE
        } ?: indexing.status.isNetworkFirstRunError()
        // PAUSED means the run was interrupted without finishing: an unmounted session,
        // a service timeout, or a network loss the user did not cause. Refusing to resume
        // left the first-run screen frozen with progress on it and one unrelated action
        // ("listen while reading"), which is a dead end. Resuming from a checkpoint is
        // safe: the session continues from the last saved batch.
        //
        // STOPPED stays terminal on purpose: it is an explicit user or system stop, and
        // resuming it silently would fight that decision.
        ManagedScanState.PAUSED -> true
        ManagedScanState.RUNNING,
        ManagedScanState.STOPPED,
        ManagedScanState.COMPLETE -> false
    }
