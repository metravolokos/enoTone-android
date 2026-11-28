package app.enotone.android

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.network.WebDavDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HTTP_STATUS_MULTI_STATUS = 207
private const val HTTP_STATUS_UNAUTHORIZED = 401
private const val HTTP_STATUS_FORBIDDEN = 403

/** Account I/O boundary; fake implementations run without Android/Room/WebDAV. */
// The endpoint-aware overloads of save/verifyOutcome exist so fakes compiled
// against the two-argument API keep working (ADR-0106, slice A1).
@Suppress("TooManyFunctions")
internal interface AccountSettingsGateway {
    suspend fun restore(): StoredWebDavLogin?

    suspend fun reauthenticationRequired(): Boolean = false

    suspend fun markReauthenticationRequired() = Unit

    /** Выход начат и не завершён (в том числе до перезапуска процесса): новые операции запрещены. */
    suspend fun signOutPending(): Boolean = false

    /** Complete only the credential tail when the durable cleanup proof is present. */
    suspend fun recoverPendingSignOut(): Boolean = false

    suspend fun verify(email: String, password: String): Boolean

    suspend fun verifyOutcome(email: String, password: String): AccountVerificationResult =
        if (verify(email, password)) AccountVerificationResult.ACCEPTED
        else AccountVerificationResult.CLOUD_UNAVAILABLE

    /** Источник музыки (ADR-0106). Реализации без него остаются на источнике Mail.ru. */
    suspend fun verifyOutcome(
        email: String,
        password: String,
        endpoint: SourceEndpoint,
    ): AccountVerificationResult = verifyOutcome(email, password)

    suspend fun save(email: String, password: String)

    /** Источник музыки (ADR-0106). Реализации без него остаются на источнике Mail.ru. */
    suspend fun save(email: String, password: String, endpoint: SourceEndpoint) = save(email, password)

    suspend fun countTracks(email: String): Int
    suspend fun signOut(email: String)
}

internal enum class AccountVerificationResult { ACCEPTED, CREDENTIALS_REJECTED, CLOUD_UNAVAILABLE }

@Suppress("TooManyFunctions")
internal class AndroidAccountSettingsGateway(
    private val context: Context,
    private val container: AppContainer,
) : AccountSettingsGateway {
    override suspend fun restore(): StoredWebDavLogin? = withContext(container.io) {
        LocalCredentialVault.restore(context)?.also { saved ->
            if (SignOutMarker.pending(context)) {
                container.accountSession.failSignOut(LibrarySnapshotPlanner.accountKey(saved.email))
            }
        }
    }

    override suspend fun reauthenticationRequired(): Boolean = withContext(container.io) {
        FirstRunPreferences(context).reauthenticationRequired()
    }

    override suspend fun markReauthenticationRequired() = withContext(container.io) {
        FirstRunPreferences(context).setReauthenticationRequired(true)
    }

    override suspend fun signOutPending(): Boolean {
        return withContext(container.io) { SignOutMarker.pending(context) }
    }

    override suspend fun recoverPendingSignOut(): Boolean = withContext(container.io) {
        val journalSourceKey = SignOutMarker.sourceKey(context)
        val cleanupConfirmed = SignOutMarker.cleanupConfirmed(context)
        if (journalSourceKey != null && cleanupConfirmed) {
            // Journal from PR #239 or later: finish only the credential tail.
            PendingSignOutRecovery(
                pending = { SignOutMarker.pending(context) },
                cleanupConfirmed = { SignOutMarker.cleanupConfirmed(context) },
                sourceKey = { SignOutMarker.sourceKey(context) },
                clearCredentials = { LocalCredentialVault.clear(context) },
                deleteArtworkManifest = { sourceKey ->
                    AccountArtworkManifest.forSource(context.noBackupFilesDir, sourceKey).clear()
                },
                finish = { SignOutMarker.finish(context) },
            ).run()
        } else {
            // Legacy journal from before #239: no proof was ever written.
            val decision = LegacySignOutRecovery.decide(
                LegacySignOutState(
                    pending = SignOutMarker.pending(context),
                    hasStoredLogin = LocalCredentialVault.restore(context) != null,
                    libraryRowCount = container.database.remoteTrackDao().countAll(),
                    hasAnyManifest = AccountArtworkManifest.hasAny(context.noBackupFilesDir),
                ),
            )
            when (decision) {
                LegacySignOutAction.NONE -> SignOutMarker.pending(context).not()
                LegacySignOutAction.BLOCKED -> false
                LegacySignOutAction.COMPLETE_TAIL -> {
                    LocalCredentialVault.clear(context)
                    // Remove the marker before the manifest: a failed marker write must
                    // leave the state re-recoverable. Deleting the manifest first would
                    // destroy the `hasAnyManifest` evidence and turn a retry into BLOCKED.
                    SignOutMarker.finish(context)
                    AccountArtworkManifest.clearAllOrphaned(context.noBackupFilesDir)
                    true
                }
            }
        }
    }

    override suspend fun verify(email: String, password: String): Boolean =
        verifyOutcome(email, password, SourceEndpoint.MAILRU_ALL_MUSIC) ==
            AccountVerificationResult.ACCEPTED

    override suspend fun verifyOutcome(
        email: String,
        password: String,
    ): AccountVerificationResult =
        verifyOutcome(email, password, SourceEndpoint.MAILRU_ALL_MUSIC)

    override suspend fun verifyOutcome(
        email: String,
        password: String,
        endpoint: SourceEndpoint,
    ): AccountVerificationResult =
        withContext(container.io) {
            // Existing bounded WebDAV probe; this never lists/imports the music folder.
            // Метка папки совпадает с правилом WebDavDiagnostics: пользовательский путь наружу не идёт.
            val folderLabel = if (endpoint.rootFolder == "/AllMusic") "PROPFIND /AllMusic"
                else "PROPFIND folder"
            val status = WebDavDiagnostics(endpoint).inspect(email, password).steps
                .firstOrNull { it.operation == folderLabel }?.httpStatus
            when (status) {
                HTTP_STATUS_MULTI_STATUS -> AccountVerificationResult.ACCEPTED
                HTTP_STATUS_UNAUTHORIZED, HTTP_STATUS_FORBIDDEN -> AccountVerificationResult.CREDENTIALS_REJECTED
                else -> AccountVerificationResult.CLOUD_UNAVAILABLE
            }
        }

    override suspend fun save(email: String, password: String) =
        save(email, password, SourceEndpoint.MAILRU_ALL_MUSIC)

    override suspend fun save(email: String, password: String, endpoint: SourceEndpoint) {
        withContext(container.io) {
            val firstRunPreferences = FirstRunPreferences(context)
            val reauthenticationRequired = firstRunPreferences.reauthenticationRequired()
            if (reauthenticationRequired) {
                val savedLogin = LocalCredentialVault.restore(context)
                check(savedLogin?.email == email) {
                    "Reauthentication must use the saved account"
                }
            }
            val preservingCompletedFirstRun = reauthenticationRequired &&
                firstRunPreferences.firstRunCompleted() == true
            if (!preservingCompletedFirstRun) firstRunPreferences.setFirstRunCompleted(false)
            LocalCredentialVault.save(context, email, password, endpoint)
            firstRunPreferences.setReauthenticationRequired(false)
        }
        // Only publish ACTIVE after the encrypted write actually succeeds.
        container.accountSession.signedIn(LibrarySnapshotPlanner.accountKey(email))
    }

    override suspend fun countTracks(email: String): Int = withContext(container.io) {
        container.database.remoteTrackDao()
            .presentCount(LibrarySnapshotPlanner.accountKey(email))
    }

    override suspend fun signOut(email: String) {
        val key = LibrarySnapshotPlanner.accountKey(email)
        container.accountSession.beginSignOut(key)
        try {
            JournaledAccountSignOut(
                writePending = { sourceKey ->
                    withContext(container.io) { SignOutMarker.begin(context, sourceKey) }
                },
                cleanup = { sourceKey -> container.accountSignOut(email).run(sourceKey) },
            ).run(key)
            withContext(container.io) { SignOutMarker.finish(context) }
            container.accountSession.finishSignOut(key)
        } catch (cancelled: CancellationException) {
            container.accountSession.failSignOut(key)
            throw cancelled
        } catch (failure: Exception) {
            container.accountSession.failSignOut(key)
            throw failure
        }
    }
}

internal enum class AccountError {
    RESTORE, CONNECTION, CREDENTIALS_REJECTED, STORAGE, OTHER_ACCOUNT, SIGN_OUT, BAD_FOLDER,
}

internal data class AccountUiState(
    /** Редактируемые поля формы входа: до проверки никуда не передаются. */
    val email: String = "",
    val password: String = "",
    /** Папка с музыкой в облаке Mail.ru (ADR-0106); менять можно только до входа. */
    val folder: String = DEFAULT_FOLDER,
    /** A reauthentication form is pinned to the saved account until the user signs out. */
    val reauthenticationEmail: String? = null,
    /** Подтверждённый вход: восстановлен из Keystore или успешно проверен и сохранён. */
    val active: StoredWebDavLogin? = null,
    /** Выход начат, но не завершён: импорт, индексация, обложки и медиатека заблокированы до повтора. */
    val signOutFailed: Boolean = false,
    val restoring: Boolean = true,
    /** Restore completed successfully and found neither saved credentials nor a reauth marker. */
    val restoreCompletedWithoutCredentials: Boolean = false,
    val checking: Boolean = false,
    val loggedIn: Boolean = false,
    val signingOut: Boolean = false,
    val showConfirmation: Boolean = false,
    val trackCount: Int = 0,
    val error: AccountError? = null,
    val failureStep: AccountSignOutFailure.Step? = null,
    val verifiedVersion: Int = 0,
    val signedOutVersion: Int = 0,
    val failedSignOutVersion: Int = 0,
) {
    val busy: Boolean get() = restoring || checking || signingOut

    /** Единственный источник входа для рабочих операций и медиатеки. */
    val workingLogin: StoredWebDavLogin? get() = libraryLogin.takeIf { !busy }

    /** Вход для медиатеки и плеера: не зависит от кратких фаз проверки, но не после незавершённого выхода. */
    val libraryLogin: StoredWebDavLogin? get() = active.takeIf { loggedIn && !signOutFailed }
}

@Suppress("TooManyFunctions") // Account form actions (email/password/folder/verify/sign-out) are one cohesive surface.
internal class AccountSettingsViewModel(
    private val gateway: AccountSettingsGateway,
    /**
     * Servername for the account form (ADR-0106). Slice A1 exposes only Cloud.Mail.ru;
     * the folder field on top of it is user-editable.
     */
    private val origin: String = SourceEndpoint.MAILRU_ALL_MUSIC.origin,
) : ViewModel() {
    private val mutable = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = mutable.asStateFlow()
    private var activeEmail: String? = null

    init {
        viewModelScope.launch {
            var pending = false
            try {
                // Read the no-backup marker before accepting any restored login as usable.
                pending = gateway.signOutPending()
                val saved = gateway.restore()
                if (pending && saved == null) {
                    if (gateway.recoverPendingSignOut()) {
                        // The marker was cleared only after the idempotent vault tail succeeded.
                        mutable.update { it.copy(signOutFailed = false, error = null) }
                    } else {
                        // Legacy/ambiguous state remains blocked; an empty vault alone is not proof.
                        mutable.update { it.copy(signOutFailed = true, error = AccountError.SIGN_OUT) }
                    }
                } else if (saved == null) {
                    if (!gateway.reauthenticationRequired()) {
                        mutable.update { it.copy(restoreCompletedWithoutCredentials = true) }
                    }
                } else {
                    if (pending) {
                        activeEmail = saved.email
                        // Keep the previous sign-out recovery path higher priority than re-login.
                        mutable.update {
                            it.copy(email = saved.email, password = saved.password, active = saved,
                                loggedIn = true, signOutFailed = true, error = AccountError.SIGN_OUT)
                        }
                    } else if (gateway.reauthenticationRequired()) {
                        // Keep the email for the form; the rejected secret is never used as a working login.
                        mutable.update {
                            it.copy(email = saved.email, password = "", active = null, loggedIn = false,
                                reauthenticationEmail = saved.email)
                        }
                    } else {
                        activeEmail = saved.email
                        // Рабочий вход — только сохранённый; правки полей во время восстановления не применяются.
                        mutable.update {
                            it.copy(email = saved.email, password = saved.password, active = saved,
                                folder = saved.endpoint.rootFolder.trim('/'), loggedIn = true,
                                signOutFailed = false)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The durable proof outranks a broken vault: recoverPendingSignOut checks
                // the marker itself, so a completed cleanup can finish its tail without
                // reading the secret. Skipping it left the user on a screen claiming the
                // login was kept, with no way out.
                if (pending && runCatching { gateway.recoverPendingSignOut() }.getOrDefault(false)) {
                    mutable.update { it.copy(signOutFailed = false, error = null) }
                    return@launch
                }
                mutable.update {
                    it.copy(
                        error = if (pending) AccountError.SIGN_OUT else AccountError.RESTORE,
                        signOutFailed = it.signOutFailed || pending,
                    )
                }
            } finally {
                mutable.update { it.copy(restoring = false) }
            }
        }
    }

    fun onEmailChanged(value: String) {
        val current = mutable.value
        if (current.loggedIn || current.signOutFailed) return
        current.reauthenticationEmail?.let { pinnedEmail ->
            mutable.update {
                it.copy(email = pinnedEmail,
                    error = if (value == pinnedEmail) null else AccountError.OTHER_ACCOUNT)
            }
            return
        }
        mutable.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChanged(value: String) {
        if (mutable.value.loggedIn || mutable.value.signOutFailed) return
        mutable.update { it.copy(password = value, error = null) }
    }

    /**
     * Папка с музыкой (ADR-0106). Менять можно только до входа: при активном аккаунте
     * смена источника идёт через «Выйти» → повторный вход, а не правкой поля.
     */
    fun onFolderChanged(value: String) {
        val current = mutable.value
        if (current.loggedIn || current.busy || current.signOutFailed) return
        mutable.update { it.copy(folder = value, error = null) }
    }

    /** Marks the currently presented account error as handled by the first-run sheet. */
    fun dismissError() {
        if (mutable.value.signOutFailed) return
        mutable.update { it.copy(error = null) }
    }

    /**
     * "Ввести заново" must actually clear the rejected password: leaving it in the field
     * makes the retry button look decorative, because Connect would resubmit the same
     * rejected value. The email is kept — only the secret is cleared.
     */
    fun clearPasswordForRetry() {
        if (mutable.value.loggedIn || mutable.value.signOutFailed) return
        mutable.update { it.copy(password = "", error = null) }
    }

    /** Failed verification must never write to the vault. */
    fun verifyConnection() {
        val current = mutable.value
        if (!current.canVerifyConnection()) return
        when (val check = current.connectionCheck(activeEmail, origin)) {
            is ConnectionCheck.Failed -> {
                mutable.update { it.copy(error = check.error) }
            }
            is ConnectionCheck.Ok -> launchVerification(current, check.endpoint)
        }
    }

    private fun launchVerification(current: AccountUiState, endpoint: SourceEndpoint) {
        mutable.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                when (gateway.verifyOutcome(current.email, current.password, endpoint)) {
                    AccountVerificationResult.CREDENTIALS_REJECTED ->
                        mutable.update { it.copy(error = AccountError.CREDENTIALS_REJECTED) }
                    AccountVerificationResult.CLOUD_UNAVAILABLE ->
                        mutable.update { it.copy(error = AccountError.CONNECTION) }
                    AccountVerificationResult.ACCEPTED -> {
                        if (mutable.value.reauthenticationEmail?.let { it != current.email } == true) {
                            mutable.update { it.copy(error = AccountError.OTHER_ACCOUNT) }
                            return@launch
                        }
                        try {
                            gateway.save(current.email, current.password, endpoint)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            mutable.update { it.copy(error = AccountError.STORAGE) }
                            return@launch
                        }
                        activeEmail = current.email
                        mutable.update {
                            it.copy(loggedIn = true, error = null,
                                active = StoredWebDavLogin(current.email, current.password, endpoint),
                                reauthenticationEmail = null,
                                verifiedVersion = it.verifiedVersion + 1)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(error = AccountError.CONNECTION) }
            } finally {
                mutable.update { it.copy(checking = false) }
            }
        }
    }

    fun requestSignOut() {
        if (!mutable.value.loggedIn || mutable.value.busy) return
        val email = activeEmail ?: return
        viewModelScope.launch {
            try {
                val count = gateway.countTracks(email)
                mutable.update { it.copy(trackCount = count, showConfirmation = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(error = AccountError.SIGN_OUT) }
            }
        }
    }

    fun cancelSignOut() {
        mutable.update { it.copy(showConfirmation = false) }
    }

    /** Reopens the login form without deleting the account's library or other local data. */
    fun requestReauthentication() {
        val current = mutable.value
        if (!current.loggedIn || current.busy) return
        viewModelScope.launch {
            try {
                gateway.markReauthenticationRequired()
                activeEmail = null
                mutable.update {
                    it.copy(password = "", active = null, loggedIn = false,
                        reauthenticationEmail = current.email,
                        signOutFailed = false, error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(error = AccountError.STORAGE) }
            }
        }
    }

    fun confirmSignOut() {
        val email = activeEmail ?: return
        if (!mutable.value.showConfirmation || mutable.value.busy) return
        mutable.update {
            it.copy(showConfirmation = false, signingOut = true, error = null)
        }
        viewModelScope.launch {
            try {
                gateway.signOut(email)
                activeEmail = null
                mutable.update {
                    it.copy(email = "", password = "", folder = DEFAULT_FOLDER, loggedIn = false, active = null,
                        signOutFailed = false, reauthenticationEmail = null,
                        trackCount = 0, failureStep = null,
                        signedOutVersion = it.signedOutVersion + 1)
                }
            } catch (cancelled: CancellationException) {
                mutable.update { it.copy(signOutFailed = true) }
                throw cancelled
            } catch (failure: AccountSignOutFailure) {
                mutable.update {
                    it.copy(error = AccountError.SIGN_OUT, failureStep = failure.step, signOutFailed = true,
                        failedSignOutVersion = it.failedSignOutVersion + 1)
                }
            } catch (_: Exception) {
                mutable.update {
                    it.copy(error = AccountError.SIGN_OUT, signOutFailed = true,
                        failedSignOutVersion = it.failedSignOutVersion + 1)
                }
            } finally {
                mutable.update { it.copy(signingOut = false) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == AccountSettingsViewModel::class.java)
                    return AccountSettingsViewModel(
                        AndroidAccountSettingsGateway(context, container),
                    ) as T
                }
            }
    }
}

private fun AccountUiState.canVerifyConnection(): Boolean =
    !isBusyOrMissingEmail() && password.isNotBlank() && error != AccountError.OTHER_ACCOUNT

/** Build the source endpoint from the form folder; null when the folder is invalid. */
private fun AccountUiState.sourceEndpoint(origin: String): SourceEndpoint? = runCatching {
    SourceEndpoint.ofUserFolder(origin, folder)
}.getOrNull()

/** Outcome of validating the login form before a network probe. */
private sealed interface ConnectionCheck {
    data class Failed(val error: AccountError) : ConnectionCheck
    data class Ok(val endpoint: SourceEndpoint) : ConnectionCheck
}

private fun AccountUiState.connectionCheck(activeEmail: String?, origin: String): ConnectionCheck =
    if (belongsToAnotherAccount(activeEmail)) {
        ConnectionCheck.Failed(AccountError.OTHER_ACCOUNT)
    } else {
        sourceEndpoint(origin)?.let(ConnectionCheck::Ok)
            ?: ConnectionCheck.Failed(AccountError.BAD_FOLDER)
    }

/** Folder path shown to the user (no leading slash); the endpoint adds the root separator. */
private const val DEFAULT_FOLDER = "AllMusic"

private fun AccountUiState.isBusyOrMissingEmail(): Boolean = busy || email.isBlank() || signOutFailed

private fun AccountUiState.belongsToAnotherAccount(activeEmail: String?): Boolean =
    belongsToAnotherSignedInAccount(activeEmail) || belongsToAnotherReauthenticationAccount()

private fun AccountUiState.belongsToAnotherSignedInAccount(activeEmail: String?): Boolean =
    loggedIn && activeEmail != email

private fun AccountUiState.belongsToAnotherReauthenticationAccount(): Boolean =
    reauthenticationEmail != null && reauthenticationEmail != email
