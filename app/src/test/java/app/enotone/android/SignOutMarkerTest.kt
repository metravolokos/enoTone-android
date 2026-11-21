package app.enotone.android

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Process-independent persisted state; no attempt to emulate Android Keystore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SignOutMarkerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val sourceKey = "a".repeat(64)

    @After fun cleanup() = SignOutMarker.finish(context)

    @Test fun pendingExitSurvivesSeparateContextAccessUntilFinished() {
        SignOutMarker.finish(context)
        assertFalse(SignOutMarker.pending(context))
        SignOutMarker.begin(context, sourceKey)
        val anotherContext = context.applicationContext
        assertTrue(SignOutMarker.pending(anotherContext))
        assertTrue(SignOutMarker.sourceKey(anotherContext) == sourceKey)
        SignOutMarker.finish(anotherContext)
        assertFalse(SignOutMarker.pending(context))
    }

    @Test fun cleanupProofIsDurableAndASecondExitStartsWithoutIt() {
        SignOutMarker.finish(context)
        SignOutMarker.begin(context, sourceKey)
        assertFalse(SignOutMarker.cleanupConfirmed(context))

        SignOutMarker.markCleanupComplete(context)
        assertTrue(SignOutMarker.cleanupConfirmed(context))

        SignOutMarker.begin(context, sourceKey)
        assertTrue(SignOutMarker.pending(context))
        assertFalse(SignOutMarker.cleanupConfirmed(context))
        assertTrue(SignOutMarker.sourceKey(context) == sourceKey)

        SignOutMarker.finish(context)
        assertFalse(SignOutMarker.pending(context))
        assertFalse(SignOutMarker.cleanupConfirmed(context))
        assertTrue(SignOutMarker.sourceKey(context) == null)
    }

    @Test fun invalidOrMissingSourceKeyCannotConfirmCleanup() {
        SignOutMarker.finish(context)
        context.getSharedPreferences(ACCOUNT_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean("sign_out_pending", true).putBoolean("sign_out_cleanup_confirmed", true)
            .putString("sign_out_source_key", "email@example.invalid").commit()

        assertFalse(SignOutMarker.cleanupConfirmed(context))
    }
}
