package app.enotone.android

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.network.WebDavDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Account I/O boundary; fake implementations run without Android/Room/WebDAV. */
internal interface AccountSettingsGateway {
    suspend fun restore(): StoredWebDavLogin?
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

    override suspend fun verify(email: String, password: String): Boolean =
        withContext(container.io) {
            // Two bounded WebDAV PROPFIND requests, no 3647-file listing/import.
            // Only successful Multi-Status for the configured library confirms login.
            WebDavDiagnostics().inspect(email, password).steps.any {
                it.operation == "PROPFIND /AllMusic" && it.httpStatus == 207
            }
        }

    override suspend fun save(email: String, password: String) = withContext(container.io) {
        LocalCredentialVault.save(context, email, password)
    }

    override suspend fun countTracks(email: String): Int = withContext(container.io) {
        container.database.remoteTrackDao()
            .presentCount(LibrarySnapshotPlanner.accountKey(email))
    }

    override suspend fun signOut(email: String) {
        container.accountSignOut(email).run(LibrarySnapshotPlanner.accountKey(email))
    }
}

internal enum class AccountError { RESTORE, CONNECTION, STORAGE, OTHER_ACCOUNT, SIGN_OUT }

internal data class AccountUiState(
    val email: String = "",
    val password: String = "",
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
}

internal class AccountSettingsViewModel(
    private val gateway: AccountSettingsGateway,
) : ViewModel() {
    private val mutable = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = mutable.asStateFlow()
    private var activeEmail: String? = null
    private var editVersion = 0

    init {
        viewModelScope.launch {
            try {
                val saved = gateway.restore()
                if (saved != null) {
                    activeEmail = saved.email
                    mutable.update {
                        it.copy(email = if (editVersion == 0) saved.email else it.email,
                            password = if (editVersion == 0) saved.password else it.password,
                            loggedIn = true)
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
        editVersion++
        mutable.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChanged(value: String) {
        editVersion++
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
                    it.copy(email = "", password = "", loggedIn = false,
                        trackCount = 0, failureStep = null,
                        signedOutVersion = it.signedOutVersion + 1)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: AccountSignOutFailure) {
                mutable.update {
                    it.copy(error = AccountError.SIGN_OUT, failureStep = failure.step,
                        failedSignOutVersion = it.failedSignOutVersion + 1)
                }
            } catch (_: Exception) {
                mutable.update {
                    it.copy(error = AccountError.SIGN_OUT,
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
