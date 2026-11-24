package app.enotone.android

import kotlinx.coroutines.CancellationException

/** Account data must be cleared before the AES-GCM key is destroyed (ADR-0080). */
interface AccountSignOutPort {
    suspend fun stopPlayback()
    suspend fun stopIndexers()
    suspend fun deleteTracks(sourceKey: String)
    suspend fun deleteArtwork(sourceKey: String)
    suspend fun resetCheckpoints(sourceKey: String)

    /** Persist cleanup completion before the final Keystore/vault deletion. */
    suspend fun markCleanupComplete()
    suspend fun deleteCredentials()
}

/** A category-only failure: never attach exception messages containing remote paths. */
class AccountSignOutFailure(val step: Step) : IllegalStateException("SIGN_OUT_${step.name}") {
    enum class Step {
        PLAYBACK, INDEXERS, TRACKS, ARTWORK, CHECKPOINTS, CREDENTIALS
    }
}

/**
 * Retryable, ordered cleanup; implementations of each operation must be idempotent.
 * A failed operation stops the sequence, so the encrypted login remains available
 * to retry deletion. No restore/playback work is allowed during this sequence.
 */
class AccountSignOut(private val port: AccountSignOutPort) {
    suspend fun run(sourceKey: String) {
        require(sourceKey.isNotBlank())
        execute(AccountSignOutFailure.Step.PLAYBACK) { port.stopPlayback() }
        execute(AccountSignOutFailure.Step.INDEXERS) { port.stopIndexers() }
        execute(AccountSignOutFailure.Step.TRACKS) { port.deleteTracks(sourceKey) }
        execute(AccountSignOutFailure.Step.ARTWORK) { port.deleteArtwork(sourceKey) }
        execute(AccountSignOutFailure.Step.CHECKPOINTS) {
            port.resetCheckpoints(sourceKey)
            port.markCleanupComplete()
        }
        execute(AccountSignOutFailure.Step.CREDENTIALS) { port.deleteCredentials() }
    }

    private suspend fun execute(step: AccountSignOutFailure.Step, action: suspend () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw AccountSignOutFailure(step)
        }
    }
}

/**
 * Stops playback and erases the account's audio data before Room and the Keystore.
 *
 * A missing MediaSession must NEVER block sign-out — disk checkpoints and the transient
 * cache still have to go. But a bound service that fails to erase MUST block it: the
 * caller keeps the vault so the user can retry (ADR-0080 fail-closed order).
 */
class AccountPlaybackStop(
    private val stopBoundService: suspend () -> Boolean,
    private val eraseAudioData: suspend () -> Unit,
    private val clearQueue: suspend () -> Unit,
) {
    suspend fun run() {
        val bound = stopBoundService() // false = service absent, not an error
        // Erase through the service when it is bound: only it can release the Media3
        // cache index and guarantee the deletion finished. Without a binding the caller
        // clears the queue files itself; the service-owned cache cannot be open then.
        if (bound) eraseAudioData()
        clearQueue()
    }
}
