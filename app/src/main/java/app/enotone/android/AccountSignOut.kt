package app.enotone.android

import kotlinx.coroutines.CancellationException

/** Account data must be cleared before the AES-GCM key is destroyed (ADR-0080). */
internal interface AccountSignOutPort {
    suspend fun stopPlayback()
    suspend fun stopIndexers()
    suspend fun deleteTracks(sourceKey: String)
    suspend fun deleteArtwork(sourceKey: String)
    suspend fun resetCheckpoints(sourceKey: String)
    suspend fun deleteCredentials()
}

/** A category-only failure: never attach exception messages containing remote paths. */
internal class AccountSignOutFailure(val step: Step) : IllegalStateException("SIGN_OUT_${step.name}") {
    internal enum class Step {
        PLAYBACK, INDEXERS, TRACKS, ARTWORK, CHECKPOINTS, CREDENTIALS
    }
}

/**
 * Retryable, ordered cleanup; implementations of each operation must be idempotent.
 * A failed operation stops the sequence, so the encrypted login remains available
 * to retry deletion. No restore/playback work is allowed during this sequence.
 */
internal class AccountSignOut(private val port: AccountSignOutPort) {
    suspend fun run(sourceKey: String) {
        require(sourceKey.isNotBlank())
        execute(AccountSignOutFailure.Step.PLAYBACK) { port.stopPlayback() }
        execute(AccountSignOutFailure.Step.INDEXERS) { port.stopIndexers() }
        execute(AccountSignOutFailure.Step.TRACKS) { port.deleteTracks(sourceKey) }
        execute(AccountSignOutFailure.Step.ARTWORK) { port.deleteArtwork(sourceKey) }
        execute(AccountSignOutFailure.Step.CHECKPOINTS) { port.resetCheckpoints(sourceKey) }
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
 * No bound MediaSession means no playback to stop. It must NEVER block
 * sign-out: disk checkpoints still need clearing before Room/Keystore.
 * A real stop exception or queue-file failure is not swallowed.
 */
internal class AccountPlaybackStop(
    private val stopBoundService: suspend () -> Boolean,
    private val clearQueue: suspend () -> Unit,
) {
    suspend fun run() {
        stopBoundService() // false = service absent, not an error
        clearQueue()
    }
}
