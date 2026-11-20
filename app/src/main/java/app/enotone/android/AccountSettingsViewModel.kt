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
internal interface AccountSettingsGateway {
    suspend fun restore(): StoredWebDavLogin?

    suspend fun reauthenticationRequired(): Boolean = false

    suspend fun markReauthenticationRequired() = Unit

    /** Выход начат и не завершён (в том числе до перезапуска процесса): новые операции запрещены. */
    suspend fun signOutPending(): Boolean = false

    suspend fun verify(email: String, password: String): Boolean

    suspend fun verifyOutcome(email: String, password: String): AccountVerificationResult =
        if (verify(email, password)) AccountVerificationResult.ACCEPTED
        else AccountVerificationResult.CLOUD_UNAVAILABLE

    suspend fun save(email: String, password: String)
    suspend fun countTracks(email: String): Int
    suspend fun signOut(email: String)
}

internal enum class AccountVerificationResult { ACCEPTED, CREDENTIALS_REJECTED, CLOUD_UNAVAILABLE }

internal class AndroidAccountSettingsGateway(
    private val context: Context,
    private val container: AppContainer,
) : AccountSettingsGateway {
    override suspend fun restore(): StoredWebDavLogin? = withContext(container.io) {
        LocalCredentialVault.restore(context)
    }

    override suspend fun reauthenticationRequired(): Boolean = withContext(container.io) {
        FirstRunPreferences(context).reauthenticationRequired()
    }

    override suspend fun markReauthenticationRequired() = withContext(container.io) {
        FirstRunPreferences(context).setReauthenticationRequired(true)
    }

    override suspend fun signOutPending(): Boolean {
        val pending = withContext(container.io) { SignOutMarker.pending(context) }
        if (pending) {
            // Блокирует медиатеку и плеер этого аккаунта так же, как неудачный выход в текущем сеансе.
            withContext(container.io) { LocalCredentialVault.restore(context) }?.let {
                container.accountSession.failSignOut(LibrarySnapshotPlanner.accountKey(it.email))
            }
        }
        return pending
    }

    override suspend fun verify(email: String, password: String): Boolean =
        verifyOutcome(email, password) == AccountVerificationResult.ACCEPTED

    override suspend fun verifyOutcome(email: String, password: String): AccountVerificationResult =
        withContext(container.io) {
            // Existing bounded WebDAV probe; this never lists/imports the music folder.
            val status = WebDavDiagnostics().inspect(email, password).steps
                .firstOrNull { it.operation == "PROPFIND /AllMusic" }?.httpStatus
            when (status) {
                HTTP_STATUS_MULTI_STATUS -> AccountVerificationResult.ACCEPTED
                HTTP_STATUS_UNAUTHORIZED, HTTP_STATUS_FORBIDDEN -> AccountVerificationResult.CREDENTIALS_REJECTED
                else -> AccountVerificationResult.CLOUD_UNAVAILABLE
            }
        }

    override suspend fun save(email: String, password: String) {
        withContext(container.io) {
            FirstRunPreferences(context).setFirstRunCompleted(false)
            LocalCredentialVault.save(context, email, password)
            FirstRunPreferences(context).setReauthenticationRequired(false)
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
        // Флаг без личных данных переживает гибель процесса: незавершённый выход блокирует работу и после запуска.
        withContext(container.io) { SignOutMarker.begin(context) }
        try {
            container.accountSignOut(email).run(key)
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

internal enum class AccountError { RESTORE, CONNECTION, CREDENTIALS_REJECTED, STORAGE, OTHER_ACCOUNT, SIGN_OUT }

internal data class AccountUiState(
    /** Редактируемые поля формы входа: до проверки никуда не передаются. */
    val email: String = "",
    val password: String = "",
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

internal class AccountSettingsViewModel(
    private val gateway: AccountSettingsGateway,
) : ViewModel() {
    private val mutable = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = mutable.asStateFlow()
    private var activeEmail: String? = null

    init {
        viewModelScope.launch {
            try {
                val saved = gateway.restore()
                if (saved == null) {
                    if (!gateway.reauthenticationRequired()) {
                        mutable.update { it.copy(restoreCompletedWithoutCredentials = true) }
                    }
                } else {
                    val pending = gateway.signOutPending()
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
                            it.copy(email = saved.email, password = "", active = null, loggedIn = false)
                        }
                    } else {
                        activeEmail = saved.email
                        // Рабочий вход — только сохранённый; правки полей во время восстановления не применяются.
                        mutable.update {
                            it.copy(email = saved.email, password = saved.password, active = saved,
                                loggedIn = true, signOutFailed = false)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(error = AccountError.RESTORE) }
            } finally {
                mutable.update { it.copy(restoring = false) }
            }
        }
    }

    fun onEmailChanged(value: String) {
        if (mutable.value.loggedIn) return
        mutable.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChanged(value: String) {
        if (mutable.value.loggedIn) return
        mutable.update { it.copy(password = value, error = null) }
    }

    /** Marks the currently presented account error as handled by the first-run sheet. */
    fun dismissError() {
        mutable.update { it.copy(error = null) }
    }

    /** Failed verification must never write to the vault. */
    fun verifyConnection() {
        val current = mutable.value
        if (current.busy || current.email.isBlank() || current.password.isBlank()) return
        if (current.loggedIn && activeEmail != current.email) {
            mutable.update { it.copy(error = AccountError.OTHER_ACCOUNT) }
            return
        }
        mutable.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                when (gateway.verifyOutcome(current.email, current.password)) {
                    AccountVerificationResult.CREDENTIALS_REJECTED ->
                        mutable.update { it.copy(error = AccountError.CREDENTIALS_REJECTED) }
                    AccountVerificationResult.CLOUD_UNAVAILABLE ->
                        mutable.update { it.copy(error = AccountError.CONNECTION) }
                    AccountVerificationResult.ACCEPTED -> {
                        try {
                            gateway.save(current.email, current.password)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            mutable.update { it.copy(error = AccountError.STORAGE) }
                            return@launch
                        }
                        activeEmail = current.email
                        mutable.update {
                            it.copy(loggedIn = true, error = null,
                                active = StoredWebDavLogin(current.email, current.password),
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
                    it.copy(email = "", password = "", loggedIn = false, active = null, signOutFailed = false,
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
