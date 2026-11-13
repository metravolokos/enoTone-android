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

    @After fun cleanup() = SignOutMarker.finish(context)

    @Test fun pendingExitSurvivesSeparateContextAccessUntilFinished() {
        SignOutMarker.finish(context)
        assertFalse(SignOutMarker.pending(context))
        SignOutMarker.begin(context)
        val anotherContext = context.applicationContext
        assertTrue(SignOutMarker.pending(anotherContext))
        SignOutMarker.finish(anotherContext)
        assertFalse(SignOutMarker.pending(context))
    }
}
