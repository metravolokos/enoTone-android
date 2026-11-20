package app.enotone.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun FirstRunAccountErrorEffect(
    accountError: AccountError?,
    phase: FirstRunPhase?,
    currentError: FirstRunError?,
    onError: (FirstRunError) -> Unit,
) {
    LaunchedEffect(accountError, phase, currentError) {
        if (phase == FirstRunPhase.LOGIN && currentError == null) {
            accountError.toFirstRunLoginError()?.let(onError)
        }
    }
}

private fun AccountError?.toFirstRunLoginError(): FirstRunError? = when (this) {
    AccountError.CREDENTIALS_REJECTED -> FirstRunError.LOGIN_CREDENTIALS
    AccountError.CONNECTION -> FirstRunError.CLOUD_UNAVAILABLE
    else -> null
}

@Composable
internal fun FirstRunImportErrorEffect(
    indexing: LibraryIndexingUiState,
    phase: FirstRunPhase?,
    hasValidatedInternet: Boolean,
    currentError: FirstRunError?,
    onError: (FirstRunError) -> Unit,
) {
    var handledVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(indexing.operationFailureVersion, phase, hasValidatedInternet, currentError) {
        if (isNewImportFailure(indexing.operationFailureVersion, handledVersion, phase)) {
            handledVersion = indexing.operationFailureVersion
            val error = indexing.operationFailure.toFirstRunError()
            if (currentError == null && shouldShowFirstRunError(error, phase, hasValidatedInternet)) {
                onError(error)
            }
        }
    }
}

private fun isNewImportFailure(version: Int, handledVersion: Int, phase: FirstRunPhase?): Boolean =
    version > handledVersion && isFirstRunIndexingPhase(phase)

private fun FirstRunIndexingFailure?.toFirstRunError(): FirstRunError = when (this) {
    FirstRunIndexingFailure.AUTHENTICATION -> FirstRunError.INDEXING_AUTHENTICATION
    FirstRunIndexingFailure.CLOUD_UNAVAILABLE -> FirstRunError.CLOUD_UNAVAILABLE
    FirstRunIndexingFailure.FILES_UNREADABLE, null -> FirstRunError.INDEXING_FILES
}

@Composable
internal fun FirstRunManagedErrorEffect(
    indexing: LibraryIndexingUiState,
    phase: FirstRunPhase?,
    hasValidatedInternet: Boolean,
    currentError: FirstRunError?,
    onError: (FirstRunError) -> Unit,
) {
    var handledErrorKey by remember { mutableStateOf<String?>(null) }
    val status = indexing.status
    val errorKey = listOf(
        status.failureStage.orEmpty(),
        status.failureReason.orEmpty(),
        status.progress?.stopCode.orEmpty(),
    ).joinToString(":")
    LaunchedEffect(phase, status.state, errorKey, hasValidatedInternet, currentError) {
        if (status.state != ManagedScanState.ERROR) {
            handledErrorKey = null
        } else if (isFirstRunIndexingPhase(phase) && errorKey != handledErrorKey) {
            handledErrorKey = errorKey
            val error = errorKey.toFirstRunError()
            if (currentError == null && shouldShowFirstRunError(error, phase, hasValidatedInternet)) {
                onError(error)
            }
        }
    }
}

internal fun shouldShowFirstRunError(
    error: FirstRunError,
    phase: FirstRunPhase?,
    hasValidatedInternet: Boolean,
): Boolean = error != FirstRunError.CLOUD_UNAVAILABLE ||
    phase != FirstRunPhase.INDEXING_OFFLINE || hasValidatedInternet

private fun isFirstRunIndexingPhase(phase: FirstRunPhase?): Boolean =
    phase == FirstRunPhase.INDEXING || phase == FirstRunPhase.INDEXING_OFFLINE

private fun String.toFirstRunError(): FirstRunError = when {
    contains("AUTH_REJECTED") -> FirstRunError.INDEXING_AUTHENTICATION
    contains("RETRY_BACKOFF") || contains("NETWORK_POLICY") -> FirstRunError.CLOUD_UNAVAILABLE
    else -> FirstRunError.INDEXING_FILES
}
