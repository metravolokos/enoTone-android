package app.enotone.android

/** Starts destructive account cleanup only after the pending journal is durably stored. */
internal class JournaledAccountSignOut(
    private val writePending: suspend (String) -> Boolean,
    private val cleanup: suspend (String) -> Unit,
) {
    suspend fun run(sourceKey: String) {
        check(writePending(sourceKey)) { "Unable to persist sign-out marker" }
        cleanup(sourceKey)
    }
}
