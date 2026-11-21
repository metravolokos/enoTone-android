package app.enotone.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirstRunPreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val sourceKey = "a".repeat(64)

    @After fun clearAccountState() {
        context.getSharedPreferences(ACCOUNT_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun `missing flag identifies a legacy install and a new instance sees committed migration`() {
        val preferences = FirstRunPreferences(context)
        assertNull(preferences.firstRunCompleted())

        assertEquals(
            FirstRunPlan(FirstRunPhase.APP, writeFirstRunCompleted = true),
            FirstRunPhasePlanner.plan(FirstRunPlanningState(
                signOutPending = false,
                hasLogin = true,
                firstRunCompleted = preferences.firstRunCompleted(),
            )),
        )
        preferences.setFirstRunCompleted(true)

        assertEquals(true, FirstRunPreferences(context.applicationContext).firstRunCompleted())
    }

    @Test fun `new installation starts incomplete and completion survives a recreated preference reader`() {
        val firstProcess = FirstRunPreferences(context)
        firstProcess.setFirstRunCompleted(false)
        assertEquals(false, FirstRunPreferences(context.applicationContext).firstRunCompleted())

        completeFirstRun(firstProcess) { }

        assertEquals(true, FirstRunPreferences(context.applicationContext).firstRunCompleted())
    }

    @Test fun `first run and sign out markers share account state without erasing one another`() {
        val preferences = FirstRunPreferences(context)
        preferences.setFirstRunCompleted(false)
        preferences.setReauthenticationRequired(true)
        SignOutMarker.begin(context, sourceKey)

        assertTrue(SignOutMarker.pending(context.applicationContext))
        assertFalse(FirstRunPreferences(context).firstRunCompleted() == true)
        assertTrue(FirstRunPreferences(context).reauthenticationRequired())

        SignOutMarker.finish(context)
        assertFalse(SignOutMarker.pending(context))
        assertEquals(false, FirstRunPreferences(context).firstRunCompleted())
        assertTrue(FirstRunPreferences(context).reauthenticationRequired())
    }

    @Test fun `sign out reset restores first run and clears reauthentication state`() {
        FirstRunPreferences(context).apply {
            setFirstRunCompleted(true)
            setReauthenticationRequired(true)
        }

        FirstRunPreferences(context.applicationContext).resetAfterSignOut()

        assertEquals(false, FirstRunPreferences(context).firstRunCompleted())
        assertFalse(FirstRunPreferences(context).reauthenticationRequired())
    }
}
