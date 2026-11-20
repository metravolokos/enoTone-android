package app.enotone.android

internal fun handleFirstRunError(
    error: FirstRunError,
    phase: FirstRunPhase,
    indexingErrorFromImport: Boolean,
    accountModel: AccountSettingsViewModel,
    indexingModel: LibraryIndexingViewModel,
) {
    when (error) {
        FirstRunError.LOGIN_CREDENTIALS -> Unit
        FirstRunError.CLOUD_UNAVAILABLE -> retryCloud(phase, accountModel, indexingModel)
        FirstRunError.INDEXING_AUTHENTICATION -> accountModel.requestReauthentication()
        FirstRunError.INDEXING_FILES -> retryIndexing(indexingErrorFromImport, indexingModel)
    }
}

private fun retryCloud(
    phase: FirstRunPhase,
    accountModel: AccountSettingsViewModel,
    indexingModel: LibraryIndexingViewModel,
) {
    if (phase == FirstRunPhase.LOGIN) accountModel.verifyConnection()
    else indexingModel.updateLibrary(fromFirstRun = true)
}

private fun retryIndexing(fromImport: Boolean, indexingModel: LibraryIndexingViewModel) {
    if (fromImport) indexingModel.updateLibrary(fromFirstRun = true)
    else indexingModel.resume(fromFirstRun = true)
}
