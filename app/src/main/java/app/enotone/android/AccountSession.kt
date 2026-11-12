package app.enotone.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** SourceKey is a SHA-256 digest: no email, password, or remote path enters this bus. */
internal enum class AccountPhase { ACTIVE, SIGNING_OUT, SIGNED_OUT, SIGN_OUT_FAILED }

internal data class AccountState(
    val sourceKey: String? = null,
    val phase: AccountPhase = AccountPhase.ACTIVE,
    val revision: Long = 0L,
) {
    /** Also blocks stale async Room reads and playback restoration during cleanup. */
    fun blocks(key: String): Boolean =
        sourceKey == key && phase != AccountPhase.ACTIVE
}

/**
 * App-scope account lifecycle. StateFlow retains the last transition even when
 * a screen is not currently collected; sign-out never depends on navigation.
 */
internal class AccountSession {
    private val mutable = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = mutable.asStateFlow()

    fun beginSignOut(sourceKey: String) = transition(sourceKey, AccountPhase.SIGNING_OUT)
    fun finishSignOut(sourceKey: String) = transition(sourceKey, AccountPhase.SIGNED_OUT)
    fun failSignOut(sourceKey: String) = transition(sourceKey, AccountPhase.SIGN_OUT_FAILED)
    fun signedIn(sourceKey: String) = transition(sourceKey, AccountPhase.ACTIVE)

    private fun transition(sourceKey: String, phase: AccountPhase) {
        require(sourceKey.isNotBlank())
        mutable.update { AccountState(sourceKey, phase, it.revision + 1) }
    }
}
