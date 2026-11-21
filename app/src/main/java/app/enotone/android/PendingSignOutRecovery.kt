package app.enotone.android

/**
 * Completes only the credential tail of a sign-out whose data cleanup was durably confirmed.
 * No confirmation means no vault deletion and no marker removal.
 */
internal class PendingSignOutRecovery(
    private val pending: () -> Boolean,
    private val cleanupConfirmed: () -> Boolean,
    private val clearCredentials: () -> Unit,
    private val finish: () -> Unit,
) {
    /** Returns true when the pending sign-out is complete or was already absent. */
    fun run(): Boolean {
        val isPending = pending()
        val canRecover = !isPending || cleanupConfirmed()
        if (isPending && canRecover) {
            clearCredentials()
            finish()
        }
        return canRecover
    }
}
