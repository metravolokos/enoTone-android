package app.enotone.android

import org.junit.Assert.assertEquals
import org.junit.Test

class FirstRunPhasePlannerTest {
    @Test
    fun followsEveryPriorityRowInAdr0101() {
        val rows = listOf(
            FirstRunPlanningState(signOutPending = true, hasLogin = true, firstRunCompleted = false) to
                FirstRunPlan(FirstRunPhase.APP),
            FirstRunPlanningState(signOutPending = false, hasLogin = false, firstRunCompleted = true) to
                FirstRunPlan(FirstRunPhase.LOGIN),
            FirstRunPlanningState(signOutPending = false, hasLogin = true, firstRunCompleted = null) to
                FirstRunPlan(FirstRunPhase.APP, writeFirstRunCompleted = true),
            FirstRunPlanningState(signOutPending = false, hasLogin = true, firstRunCompleted = false,
                indexingActive = true, hasValidatedInternet = true) to FirstRunPlan(FirstRunPhase.INDEXING),
            FirstRunPlanningState(signOutPending = false, hasLogin = true, firstRunCompleted = false,
                indexingActive = true, hasValidatedInternet = false) to
                FirstRunPlan(FirstRunPhase.INDEXING_OFFLINE),
            FirstRunPlanningState(signOutPending = false, hasLogin = true, firstRunCompleted = false,
                indexingComplete = true, songCount = 12) to FirstRunPlan(FirstRunPhase.DONE),
            FirstRunPlanningState(signOutPending = false, hasLogin = true, firstRunCompleted = false,
                indexingComplete = true, songCount = 0) to
                FirstRunPlan(FirstRunPhase.APP, writeFirstRunCompleted = true),
            FirstRunPlanningState(signOutPending = false, hasLogin = true, firstRunCompleted = true) to
                FirstRunPlan(FirstRunPhase.APP),
        )

        rows.forEach { (input, expected) ->
            assertEquals("$input", expected, FirstRunPhasePlanner.plan(input))
        }
    }

    @Test
    fun cleanInstallationWritesFalseAfterSuccessfulEmptyCredentialRestore() {
        val plan = FirstRunPhasePlanner.plan(
            FirstRunPlanningState(
                signOutPending = false,
                hasLogin = false,
                firstRunCompleted = null,
                restoreCompletedWithoutCredentials = true,
            ),
        )

        assertEquals(FirstRunPlan(FirstRunPhase.LOGIN, writeFirstRunCompleted = false), plan)
    }

    @Test
    fun restoreFailureAndReauthenticationWithoutEmailDoNotWriteFirstRunFlag() {
        listOf(null, false, true).forEach { completedFlag ->
            // Restore errors and reauthentication with no recoverable email both leave the
            // explicit "restore completed without credentials" signal false.
            val plan = FirstRunPhasePlanner.plan(
                FirstRunPlanningState(
                    signOutPending = false,
                    hasLogin = false,
                    firstRunCompleted = completedFlag,
                    restoreCompletedWithoutCredentials = false,
                ),
            )

            assertEquals(FirstRunPlan(FirstRunPhase.LOGIN), plan)
        }
    }

    @Test
    fun completedImportShowsDoneOrReturnsToAppWhenEmpty() {
        val nonEmpty = FirstRunPlanningState(
            signOutPending = false,
            hasLogin = true,
            firstRunCompleted = false,
            libraryImportFinished = true,
            songCount = 8,
            pendingTagCount = 0,
        )
        val empty = nonEmpty.copy(songCount = 0)

        assertEquals(FirstRunPhase.DONE, FirstRunPhasePlanner.plan(nonEmpty).phase)
        assertEquals(
            FirstRunPlan(FirstRunPhase.APP, writeFirstRunCompleted = true),
            FirstRunPhasePlanner.plan(empty),
        )
    }
}
