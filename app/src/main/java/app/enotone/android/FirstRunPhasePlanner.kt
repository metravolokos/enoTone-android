package app.enotone.android

/** The first-run route is deliberately separate from the regular app navigation. */
internal enum class FirstRunPhase { LOGIN, INDEXING, INDEXING_OFFLINE, DONE, APP }

internal data class FirstRunPlanningState(
    val signOutPending: Boolean,
    val hasLogin: Boolean,
    /** Null means the installation predates the first-run flag. */
    val firstRunCompleted: Boolean?,
    val indexingActive: Boolean = false,
    val indexingComplete: Boolean = false,
    val libraryImportFinished: Boolean = false,
    val songCount: Int? = null,
    val pendingTagCount: Int? = null,
    val hasValidatedInternet: Boolean = true,
)

internal data class FirstRunPlan(
    val phase: FirstRunPhase,
    /** A non-null value is persisted by the caller before the next app launch. */
    val writeFirstRunCompleted: Boolean? = null,
)

/** Pure implementation of the priority table in ADR-0101. */
internal object FirstRunPhasePlanner {
    fun plan(state: FirstRunPlanningState): FirstRunPlan = when {
        state.signOutPending -> appPhase()
        !state.hasLogin -> FirstRunPlan(FirstRunPhase.LOGIN, writeFirstRunCompleted = false)
        // Existing installs keep their old entry point. Migrate before reading library state.
        state.firstRunCompleted == null -> appPhase(writeCompleted = true)
        state.firstRunCompleted -> appPhase()
        state.indexingActive -> indexingPhase(state.hasValidatedInternet)
        state.indexingComplete -> completedPhase(state.songCount)
        state.libraryImportFinished -> importedPhase(state)
        else -> indexingPhase(state.hasValidatedInternet)
    }

    private fun importedPhase(state: FirstRunPlanningState): FirstRunPlan = when {
        state.songCount == null -> indexingPhase(state.hasValidatedInternet)
        state.songCount == 0 && state.pendingTagCount.orZero() == 0 -> appPhase(writeCompleted = true)
        state.pendingTagCount.orZero() == 0 -> FirstRunPlan(FirstRunPhase.DONE)
        else -> indexingPhase(state.hasValidatedInternet)
    }

    private fun completedPhase(songCount: Int?): FirstRunPlan = when {
        songCount == null -> FirstRunPlan(FirstRunPhase.INDEXING)
        songCount == 0 -> FirstRunPlan(FirstRunPhase.APP, writeFirstRunCompleted = true)
        else -> FirstRunPlan(FirstRunPhase.DONE)
    }

    private fun indexingPhase(hasValidatedInternet: Boolean) = FirstRunPlan(
        if (hasValidatedInternet) FirstRunPhase.INDEXING else FirstRunPhase.INDEXING_OFFLINE,
    )

    private fun appPhase(writeCompleted: Boolean? = null) =
        FirstRunPlan(FirstRunPhase.APP, writeFirstRunCompleted = writeCompleted)

    private fun Int?.orZero() = this ?: 0
}
