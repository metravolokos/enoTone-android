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

/** Account I/O boundary; fake implementations run without Android/Room/WebDAV. */
internal interface AccountSettingsGateway {
    suspend fun restore(): StoredWebDavLogin?

    /** Выход начат и не завершён (в том числе до перезапуска процесса): новые операции запрещены. */
    suspend fun signOutPending(): Boolean = false

    suspend fun verify(email: String, password: String): Boolean
    suspend fun save(email: String, password: String)
    suspend fun countTracks(email: String): Int
    suspend fun signOut(email: String)
}

internal class AndroidAccountSettingsGateway(
    private val context: Context,
    private val container: AppContainer,
) : AccountSettingsGateway {
    override suspend fun restore(): StoredWebDavLogin? = withContext(container.io) {
        LocalCredentialVault.restore(context)
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
        withContext(container.io) {
            // Two bounded WebDAV PROPFIND requests, no 3647-file listing/import.
            // Only successful Multi-Status for the configured library confirms login.
            WebDavDiagnostics().inspect(email, password).steps.any {
                it.operation == "PROPFIND /AllMusic" && it.httpStatus == 207
            }
        }

    override suspend fun save(email: String, password: String) {
        withContext(container.io) { LocalCredentialVault.save(context, email, password) }
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

internal enum class AccountError { RESTORE, CONNECTION, STORAGE, OTHER_ACCOUNT, SIGN_OUT }

internal data class AccountUiState(
    /** Редактируемые поля формы входа: до проверки никуда не передаются. */
    val email: String = "",
    val password: String = "",
    /** Подтверждённый вход: восстановлен из Keystore или успешно проверен и сохранён. */
    val active: StoredWebDavLogin? = null,
    /** Выход начат, но не завершён: импорт, индексация, обложки и медиатека заблокированы до повтора. */
    val signOutFailed: Boolean = false,
    val restoring: Boolean = true,
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
                if (saved != null) {
                    activeEmail = saved.email
                    val pending = gateway.signOutPending()
                    // Рабочий вход — только сохранённый; правки полей во время восстановления не применяются.
                    mutable.update {
                        it.copy(email = saved.email, password = saved.password, active = saved,
                            loggedIn = true, signOutFailed = pending,
                            error = if (pending) AccountError.SIGN_OUT else it.error)
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
                val accepted = gateway.verify(current.email, current.password)
                if (!accepted) {
                    mutable.update { it.copy(error = AccountError.CONNECTION) }
                } else {
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
