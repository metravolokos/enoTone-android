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
        hasValidatedInternet,
    ) {
        if (plan?.phase !in setOf(FirstRunPhase.INDEXING, FirstRunPhase.INDEXING_OFFLINE)) {
            return@LaunchedEffect
        }
        if (!hasValidatedInternet) {
            awaitingNetworkRecovery = true
            return@LaunchedEffect
        }
        if (awaitingNetworkRecovery) {
            if (canRetryFirstRunImport(account, indexing)) {
                awaitingNetworkRecovery = false
                started = true
                onAutomaticRetry()
                indexingModel.retryFirstRun()
            }
            return@LaunchedEffect
        }
        if (shouldStartFirstRunImport(account, indexing, plan, hasValidatedInternet, started)) {
            started = true
            indexingModel.updateLibrary(fromFirstRun = true)
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
        hasValidatedInternet && !alreadyStarted
}

private fun canRetryFirstRunImport(
    account: AccountUiState,
    indexing: LibraryIndexingUiState,
): Boolean = account.libraryLogin != null && indexing.hasLogin && !indexing.busy &&
    !indexing.status.active && !indexing.status.terminating && !indexing.permissionPending
