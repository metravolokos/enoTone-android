package app.enotone.android

/**
 * Completes only the credential tail of a sign-out whose data cleanup was durably confirmed.
 * No confirmation means no vault deletion and no marker removal.
 */
internal class PendingSignOutRecovery(
    private val pending: () -> Boolean,
    private val cleanupConfirmed: () -> Boolean,
    private val sourceKey: () -> String?,
    private val clearCredentials: () -> Unit,
    private val deleteArtworkManifest: (String) -> Unit,
    private val finish: () -> Unit,
) {
    /** Returns true when the pending sign-out is complete or was already absent. */
    fun run(): Boolean {
        val isPending = pending()
        val journalSourceKey = sourceKey()
        val canRecover = !isPending || (cleanupConfirmed() && journalSourceKey != null)
        if (isPending && canRecover) {
            clearCredentials()
            deleteArtworkManifest(checkNotNull(journalSourceKey))
            finish()
        }
        return canRecover
    }
}
